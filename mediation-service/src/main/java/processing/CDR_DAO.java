package processing;

import db.DB;
import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT: Data Access Object (DAO) — the only class that writes CDR data to
 *       the Mediation NeonDB. Contains two static methods:
 *         insertCdr()        → writes one processed CDR to mediation_cdr
 *         insertAggregated() → writes one aggregation bucket to mediation_cdr_aggregated
 *
 * WHY a separate DAO class?
 *   Without this layer, SQL strings would be scattered across CDRBuffer,
 *   CDRAggregator, and other classes — any schema change would require
 *   hunting through many files. CDR_DAO is a single point of change:
 *   if a column is renamed or added, only this file needs updating.
 *
 * DESIGN — static methods (no instance needed):
 *   CDR_DAO has no state of its own — it's a collection of DB operations.
 *   Static methods mean callers don't need to create or inject a DAO object
 *   (CDRBuffer can just call CDR_DAO.insertCdr() directly).
 *
 * SQL CONCEPTS:
 *   ON CONFLICT … DO NOTHING — idempotent insert. If a CDR with the same
 *   (dial_a, dial_b, start_time, duration) already exists in the DB (e.g.
 *   the buffer was flushed twice due to a crash), the duplicate row is
 *   silently ignored instead of throwing a UniqueConstraintViolation.
 *   This makes the entire pipeline "at-least-once safe".
 *
 * FIELD MAPPING (CDR type → mediation_cdr columns):
 *   CDR type     │ dial_a         │ dial_b          │ duration         │ service_id
 *   MscVoiceCdr  │ callingNumber  │ calledNumber    │ callDuration (s) │ 1
 *   SmscCdr      │ senderMSISDN   │ receiverMSISDN  │ 1 (per message)  │ 3
 *   PgwDataCdr   │ servedMSISDN   │ "internet"      │ totalBytes (B)   │ 2
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class CDR_DAO {

    /**
     * Writes one enriched CDR to the mediation_cdr table.
     *
     * WHY extract fields first (dialA, dialB, …)?
     *   The three CDR types (MscVoiceCdr, SmscCdr, PgwDataCdr) each store
     *   the same conceptual data under different field names. The private
     *   helper methods below normalise them into a common set of variables
     *   before the INSERT, keeping the SQL clean and readable.
     *
     * WHY ON CONFLICT DO NOTHING?
     *   The DuplicateDetector (in-memory) catches most duplicates before this
     *   point. But if the JVM restarts mid-flush, a CDR could be re-processed
     *   from the FTP server. The DB-level UNIQUE constraint on
     *   (dial_a, dial_b, start_time, duration) is the final safety net —
     *   ON CONFLICT DO NOTHING makes it silently safe to retry.
     *
     * @param cdr        the raw CDR object (any of the three types)
     * @param info       enrichment data from CDREnricher (carrier, region, hplmn)
     * @param sourceFile the FTP filename this CDR was decoded from (for traceability)
     */
    public static void insertCdr(Object cdr,
                                 CDREnricher.SubscriberInfo info,
                                 String sourceFile,
                                 String rejectionReason) {
        // Normalise fields from whichever CDR type we received
        String dialA      = getDialA(cdr);
        String dialB      = getDialB(cdr);
        String startTime  = getStartTime(cdr);
        long   duration   = getDuration(cdr);
        int    serviceId  = getServiceId(cdr);
        String recordType = cdr.getClass().getSimpleName(); // "MscVoiceCdr", "SmscCdr", "PgwDataCdr"

        try {
            DB.executeUpdate("""
                INSERT INTO mediation_cdr
                  (dial_a, dial_b, start_time, duration, service_id,
                   hplmn, external_charges, record_type, source_file, rejection_reason)
                VALUES (?,?, CAST(? AS TIMESTAMP),?,?,?,?,?,?,?)
                ON CONFLICT (dial_a, dial_b, start_time, duration) DO NOTHING
            """,
                    dialA, dialB, startTime, duration, serviceId,
                    info != null ? info.hplmn() : null, // null if subscriber not in CSV
                    0.00,       // external_charges: always 0 at mediation layer
                    recordType,
                    sourceFile,
                    rejectionReason);
        } catch (Exception e) {
            System.err.println("CdrDao.insertCdr failed: " + e.getMessage());
        }
    }

    /**
     * Writes one aggregation bucket to the mediation_cdr_aggregated table.
     *
     * Called by CDRAggregator.flushToDB() — one call per hourly or daily bucket.
     *
     * @param r           the bucket containing accumulated counters
     * @param windowType  "HOURLY" or "DAILY"
     * @param windowStart start of the time window (e.g. "2024-05-10")
     * @param windowEnd   end of the time window   (e.g. "2024-05-10:59:59")
     */
    public static void insertAggregated(CDRAggregator.AggregationResult r,
                                        String windowType,
                                        String windowStart, String windowEnd) {
        try {
            DB.executeUpdate("""
                INSERT INTO mediation_cdr_aggregated
                  (record_type, dial_a, window_type, window_start, window_end,
                   total_records, total_duration, total_bytes, total_messages)
                VALUES (?,?,?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ),?,?,?,?)
            """,
                    r.recordType, r.msisdn, windowType, windowStart, windowEnd,
                    r.totalRecords, r.totalDuration, r.totalBytes, r.totalMessages);
        } catch (Exception e) {
            System.err.println("CdrDao.insertAggregated failed: " + e.getMessage());
        }
    }

    // ─── Field extraction helpers ─────────────────────────────────────────────
    // Each method uses instanceof pattern matching to extract the same
    // conceptual field from whichever CDR type was passed in.

    /** dial_a = the originating party (caller / sender / data subscriber) */
    private static String getDialA(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callingNumber;
        if (cdr instanceof SmscCdr s)     return s.senderMSISDN;
        if (cdr instanceof PgwDataCdr p)  return p.servedMSISDN;
        return null;
    }

    /** dial_b = the destination party.
     *  PGW data sessions have no "called number" — billing expects "internet". */
    private static String getDialB(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.calledNumber;
        if (cdr instanceof SmscCdr s)     return s.receiverMSISDN;
        if (cdr instanceof PgwDataCdr)    return "internet"; // billing schema expectation
        return null;
    }

    /** The timestamp when the CDR event began — stored as the start_time column. */
    private static String getStartTime(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callStartTime;
        if (cdr instanceof SmscCdr s)     return s.submissionTime;
        if (cdr instanceof PgwDataCdr p)  return p.startTime;
        return null;
    }

    /**
     * The 'duration' column stores different units per CDR type:
     *   Voice → seconds of call time
     *   SMS   → 1 (each message counts as 1 unit)
     *   Data  → bytes transferred (not seconds — different dimension!)
     * The billing trigger on NeonDB knows the service_id and interprets
     * this field accordingly.
     */
    private static long getDuration(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callDuration; // seconds
        if (cdr instanceof SmscCdr)       return 1L;              // 1 per message unit
        if (cdr instanceof PgwDataCdr p)  return p.totalBytes;    // bytes, NOT seconds
        return 0L;
    }

    /**
     * service_id maps CDR type to the billing system's service catalogue:
     *   1 = Voice, 2 = Data, 3 = SMS
     * The billing auto-rating trigger uses service_id to pick the correct tariff.
     */
    private static int getServiceId(Object cdr) {
        if (cdr instanceof MscVoiceCdr) return 1; // Voice
        if (cdr instanceof PgwDataCdr)  return 2; // Data
        if (cdr instanceof SmscCdr)     return 3; // SMS
        return 0;
    }
}
