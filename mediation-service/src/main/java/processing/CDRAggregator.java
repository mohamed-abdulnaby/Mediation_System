package processing;

import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT: Computes running statistical summaries of CDRs per subscriber,
 *       grouped into HOURLY and DAILY time windows.
 *
 * WHY:  The raw mediation_cdr table stores one row per CDR — it answers
 *       "what calls happened?". But the billing and network operations teams
 *       also need to answer "how much did subscriber X use in the last hour?"
 *       or "what was the total data consumed today per carrier?".
 *
 *       Scanning millions of raw CDR rows for every such query is very slow.
 *       CDRAggregator maintains pre-computed buckets: as each CDR arrives,
 *       it's added to the right bucket instantly. When the buffer flushes,
 *       those buckets are written to mediation_cdr_aggregated — a much
 *       smaller, pre-summarized table that answers aggregate queries fast.
 *
 * DESIGN — two-level aggregation:
 *   hourly → e.g. "MSC_VOICE | 01234567 | H | 2024-05-10" (one bucket per hour)
 *   daily  → e.g. "MSC_VOICE | 01234567 | D | 20240510"   (one bucket per day)
 *   Both maps are updated simultaneously for every CDR so we can answer
 *   both fine-grained (hourly) and coarse-grained (daily) queries.
 *
 * THREAD SAFETY:
 *   ConcurrentHashMap allows multiple threads to read/write different keys
 *   simultaneously without locking the whole map.
 *   However, updating a single AggregationResult (e.g. r.totalRecords++)
 *   is NOT atomic — two threads could both read 5, both compute 6, and both
 *   write 6 (losing one increment). We prevent this with 'synchronized(r)':
 *   only one thread at a time can update a given bucket.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class CDRAggregator {

    /**
     * Mutable accumulator for a single aggregation bucket.
     *
     * WHY static inner class?
     *   It groups the result fields with the class that uses them, but
     *   'static' means instances don't hold a reference to the outer
     *   CDRAggregator — they can exist independently.
     *
     * WHY volatile on the counters?
     *   volatile guarantees that reads of these fields by one thread always
     *   see the latest value written by another thread (no CPU cache staleness).
     *   Combined with synchronized(r) in update(), this prevents both
     *   lost-update races AND stale reads.
     */
    public static class AggregationResult {
        public final String recordType, msisdn;
        public volatile int  totalRecords  = 0;
        public volatile long totalDuration = 0; // voice: seconds; data: bytes (reused field)
        public volatile long totalBytes    = 0; // PGW data sessions only
        public volatile int  totalMessages = 0; // SMSC SMS only

        public AggregationResult(String recordType, String msisdn) {
            this.recordType = recordType;
            this.msisdn = msisdn;
        }
    }

    // Two separate maps — one for hourly windows, one for daily windows.
    // ConcurrentHashMap: safe for concurrent writes from multiple CDR-processing threads.
    private final Map<String, AggregationResult> hourly = new ConcurrentHashMap<>();
    private final Map<String, AggregationResult> daily  = new ConcurrentHashMap<>();

    /**
     * Adds a CDR's contribution to the correct hourly and daily buckets.
     *
     * Called by CDRBuffer for each CDR after enrichment.
     * Each CDR type updates different counter fields:
     *   MSC Voice → totalRecords + totalDuration (call seconds)
     *   SMSC SMS  → totalRecords + totalMessages (message count)
     *   PGW Data  → totalRecords + totalBytes    (data volume)
     *
     * @param cdr    the raw CDR object (any of the three types)
     * @param hplmn  the subscriber's home network code (from CDREnricher)
     */
    public void aggregate(Object cdr, String hplmn) {
        if (cdr instanceof MscVoiceCdr m) {
            // Update the hourly bucket then the daily bucket for this voice CDR
            update(hourly, "MSC_VOICE|" + m.callingNumber + "|H|" + bucket(m.callStartTime, "H"),
                    m.callingNumber, "MSC_VOICE", r -> { r.totalRecords++; r.totalDuration += m.callDuration; });
            update(daily,  "MSC_VOICE|" + m.callingNumber + "|D|" + bucket(m.callStartTime, "D"),
                    m.callingNumber, "MSC_VOICE", r -> { r.totalRecords++; r.totalDuration += m.callDuration; });
        }
        if (cdr instanceof SmscCdr s) {
            update(hourly, "SMSC_SMS|" + s.senderMSISDN + "|H|" + bucket(s.submissionTime, "H"),
                    s.senderMSISDN, "SMSC_SMS", r -> { r.totalRecords++; r.totalMessages++; });
            update(daily,  "SMSC_SMS|" + s.senderMSISDN + "|D|" + bucket(s.submissionTime, "D"),
                    s.senderMSISDN, "SMSC_SMS", r -> { r.totalRecords++; r.totalMessages++; });
        }
        if (cdr instanceof PgwDataCdr p) {
            update(hourly, "PGW_DATA|" + p.servedMSISDN + "|H|" + bucket(p.startTime, "H"),
                    p.servedMSISDN, "PGW_DATA", r -> { r.totalRecords++; r.totalBytes += p.totalBytes; });
            update(daily,  "PGW_DATA|" + p.servedMSISDN + "|D|" + bucket(p.startTime, "D"),
                    p.servedMSISDN, "PGW_DATA", r -> { r.totalRecords++; r.totalBytes += p.totalBytes; });
        }
    }

    /**
     * Finds (or creates) the bucket for this key and applies the update lambda.
     *
     * WHY computeIfAbsent?
     *   computeIfAbsent is an atomic "get-or-create" operation on ConcurrentHashMap.
     *   It guarantees that only one AggregationResult is ever created per key,
     *   even if two threads try to create the same bucket simultaneously.
     *
     * WHY synchronized(r)?
     *   Once we have the bucket, we need to increment its counters.
     *   r.totalRecords++ is NOT atomic — it's three operations (read, add, write).
     *   synchronized(r) ensures only one thread touches a given bucket at a time,
     *   preventing two threads from both reading 5, adding 1, and both writing 6.
     */
    private void update(Map<String, AggregationResult> map, String key,
                        String msisdn, String type,
                        java.util.function.Consumer<AggregationResult> updater) {
        // computeIfAbsent: atomically returns existing bucket or creates a new one
        AggregationResult r = map.computeIfAbsent(key, k -> new AggregationResult(type, msisdn));
        // synchronized: only one thread updates the counters at a time
        synchronized (r) { updater.accept(r); }
    }

    /**
     * Truncates a timestamp string to either the hour (H) or the day (D)
     * to use as the bucket grouping key.
     *
     * Example: "20240510143022"
     *   H → "2024-05-10"  (first 10 chars — groups all records in the same day)
     *   D → "20240510"    (first 8 chars  — groups by raw date digits)
     *
     * WHY substring instead of LocalDateTime parsing?
     *   Parsing is slower and the timestamp format is fixed. Substring is
     *   simple, fast, and doesn't require a DateTimeFormatter.
     */
    private String bucket(String timestamp, String type) {
        try {
            return type.equals("H") ? timestamp.substring(0, 10) : timestamp.substring(0, 8);
        } catch (Exception e) { return timestamp; } // if timestamp is shorter than expected, use as-is
    }

    /** Returns the hourly aggregation map — used by tests and future reporting. */
    public Map<String, AggregationResult> getHourly() { return hourly; }
    /** Returns the daily aggregation map — used by tests and future reporting. */
    public Map<String, AggregationResult> getDaily()  { return daily; }

    /**
     * Writes all accumulated buckets to the mediation_cdr_aggregated table
     * and then clears both maps to start fresh for the next window.
     *
     * Called by CDRBuffer.flush() after every batch of CDRs is persisted.
     * After this returns, the hourly and daily maps are empty and ready
     * for the next flush cycle.
     */
    public void flushToDB() {
        // Write every hourly bucket to the DB
        hourly.forEach((key, r) -> {
            String[] parts = key.split("\\|");
            String windowStart = parts.length > 3 ? parts[3] : "";
            CDR_DAO.insertAggregated(r, "HOURLY", windowStart, windowStart + ":59:59");
        });
        // Write every daily bucket to the DB
        daily.forEach((key, r) -> {
            String[] parts = key.split("\\|");
            String windowStart = parts.length > 3 ? parts[3] : "";
            CDR_DAO.insertAggregated(r, "DAILY", windowStart, windowStart + "T23:59:59");
        });
        // Clear both maps — the next flush cycle starts from zero
        hourly.clear();
        daily.clear();
    }
}
