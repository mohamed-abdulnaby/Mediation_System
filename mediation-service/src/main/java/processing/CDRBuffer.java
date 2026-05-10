package processing;

import java.util.*;
import java.util.concurrent.*;

public class CDRBuffer {

    private static final int  BATCH_SIZE         = 100;
    private static final long FLUSH_INTERVAL_SEC = 5;

    // Wrapper to keep CDR and its enrichment info together through the queue
    private record CdrEntry(Object cdr, CDREnricher.SubscriberInfo info) {}

    private final LinkedBlockingQueue<CdrEntry> queue = new LinkedBlockingQueue<>(10_000);
    private final CDRSorter      sorter;
    private final CDRAggregator  aggregator;
    private final Runnable       onFlush; // triggers Person C's CSVFormatter

    public CDRBuffer(Runnable onFlush, CDRAggregator aggregator) {
        this.onFlush    = onFlush;
        this.aggregator = aggregator;
        this.sorter     = new CDRSorter();
        startTimedFlusher();
    }

    public void add(Object cdr, CDREnricher.SubscriberInfo info) {
        CdrEntry entry = new CdrEntry(cdr, info);
        if (!queue.offer(entry)) {
            System.err.println("CDRBuffer: FULL — emergency flush");
            flush();
            queue.offer(entry);
        }
        if (queue.size() >= BATCH_SIZE) flush();
    }

    public synchronized void flush() {
        if (queue.isEmpty()) return;
        List<CdrEntry> batch = new ArrayList<>();
        queue.drainTo(batch, BATCH_SIZE);

        // Sort by timestamp before persisting
        batch.sort(Comparator.comparing(e -> sorter.getTimestamp(e.cdr())));

        // Persist each CDR with its paired enrichment info
        for (CdrEntry entry : batch) {
            CDR_DAO.insertCdr(entry.cdr(), entry.info(), null);
        }

        // Flush aggregation buckets to DB
        aggregator.flushToDB();

        // Trigger Person C's CSV generation + RMI send
        if (onFlush != null) onFlush.run();
    }

    private void startTimedFlusher() {
        Executors.newSingleThreadScheduledExecutor()
                .scheduleAtFixedRate(this::flush,
                        FLUSH_INTERVAL_SEC, FLUSH_INTERVAL_SEC, TimeUnit.SECONDS);
    }
}
