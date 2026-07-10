package processing;

import java.util.*;
import java.util.concurrent.*;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT: Thread-safe in-memory staging area. Collects CDRs and flushes them
 *       to the database in sorted batches — when 100 records accumulate OR
 *       every 5 seconds, whichever comes first.
 *
 * WHY:  Writing one DB row per CDR as they arrive (one INSERT per record)
 *       creates enormous overhead: each insert costs a round-trip to NeonDB
 *       (~20–50ms). At 3 CDRs/sec the system would fall behind immediately.
 *       Batching amortises that cost across 100 records per flush.
 *
 * DATA STRUCTURE — LinkedBlockingQueue:
 *   - Thread-safe: multiple FTP threads can call add() concurrently
 *   - Bounded (10,000 cap): prevents unbounded memory growth if the DB is slow
 *   - drainTo(): bulk-transfers N entries atomically — faster than N poll() calls
 *
 * FLUSH TRIGGERS:
 *   1. Size-based: when queue.size() >= BATCH_SIZE (100), flush immediately
 *   2. Time-based: ScheduledExecutorService fires flush() every 5 seconds
 *      (ensures records are persisted even during low-traffic periods)
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class CDRBuffer {

    /** Maximum records per flush — keeps individual DB transactions bounded. */
    private static final int  BATCH_SIZE         = 100;
    /** Time-based flush interval — guarantees persistence within 5 seconds even
     *  when BATCH_SIZE is never reached (e.g. low-traffic night hours). */
    private static final long FLUSH_INTERVAL_SEC = 5;

    /**
     * Internal pairing of a CDR with its enrichment data.
     *
     * WHY pair them? CDREnricher.lookup() runs upstream in FtpProcessor.
     * Carrying the SubscriberInfo through the queue avoids a redundant
     * re-lookup during flush().
     *
     * WHY a record? Immutable value data — compiler generates constructor,
     * getters, equals, hashCode, toString with zero boilerplate.
     */
    private record CdrEntry(Object cdr, CDREnricher.SubscriberInfo info, String sourceFile) {}

    // Bounded at 10,000 — if the DB is slow and the queue fills up,
    // offer() returns false and we trigger an emergency flush.
    private final LinkedBlockingQueue<CdrEntry> queue = new LinkedBlockingQueue<>(10_000);

    private final CDRSorter     sorter;     // sorts each batch chronologically before INSERT
    private final CDRAggregator aggregator; // accumulates running totals per subscriber/window
    private final Runnable      onFlush;    // callback → Person C's CSVFormatter + RMI send

    /**
     * @param onFlush     called after each flush — triggers CSV generation and RMI delivery
     * @param aggregator  shared aggregator accumulating stats across flush cycles
     */
    public CDRBuffer(Runnable onFlush, CDRAggregator aggregator) {
        this.onFlush    = onFlush;
        this.aggregator = aggregator;
        this.sorter     = new CDRSorter();
        startTimedFlusher(); // start the background 5-second timer immediately
    }

    /**
     * Adds a CDR and its enrichment data to the queue.
     *
     * WHY offer() not put()?
     *   put() blocks the calling thread if the queue is full.
     *   offer() returns false immediately → we can emergency-flush and retry,
     *   keeping the FTP pipeline moving without blocking.
     */
    public void add(Object cdr, CDREnricher.SubscriberInfo info, String sourceFile) {
        CdrEntry entry = new CdrEntry(cdr, info, sourceFile);
        if (!queue.offer(entry)) {
            // Queue backed up to 10,000 — emergency flush to make room
            System.err.println("CDRBuffer: FULL — emergency flush");
            flush();
            queue.offer(entry); // retry — queue is now drained
        }
        if (queue.size() >= BATCH_SIZE) flush(); // size-based trigger
    }

    /**
     * Drains up to BATCH_SIZE records, sorts them, persists to NeonDB,
     * flushes aggregation buckets, and triggers CSV delivery.
     *
     * WHY synchronized?
     *   The timed flusher (background thread) and add() (FTP threads) can
     *   both call flush() concurrently. synchronized ensures only one flush
     *   runs at a time, preventing double-writes of the same records.
     *
     * WHY sort before INSERT?
     *   CDRs arrive in download order, not event order. Chronological rows
     *   in the DB are required by the billing system for session reconstruction.
     */
    public synchronized void flush() {
        if (queue.isEmpty()) return;
        List<CdrEntry> batch = new ArrayList<>();
        queue.drainTo(batch, BATCH_SIZE); // atomic bulk transfer

        // Sort by timestamp before writing — billing depends on chronological order
        batch.sort(Comparator.comparing(e -> sorter.getTimestamp(e.cdr())));

        // Persist each CDR with its enrichment info to mediation_cdr
        for (CdrEntry entry : batch) {
            CDR_DAO.insertCdr(entry.cdr(), entry.info(), entry.sourceFile(), null);
        }

        aggregator.flushToDB();     // write aggregation buckets → mediation_cdr_aggregated
        if (onFlush != null) onFlush.run(); // trigger Person C: CSV + RMI send
    }

    /**
     * Starts a single background thread that calls flush() every 5 seconds.
     *
     * WHY ScheduledExecutorService not Thread.sleep()?
     *   scheduleAtFixedRate() fires every N seconds from the FIRST execution —
     *   it doesn't drift. A sleep loop drifts by the execution time of each cycle.
     */
    private void startTimedFlusher() {
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::flush,
                        FLUSH_INTERVAL_SEC, FLUSH_INTERVAL_SEC, TimeUnit.SECONDS);
    }
}
