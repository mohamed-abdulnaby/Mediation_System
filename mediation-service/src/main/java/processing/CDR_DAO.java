package processing;

import db.DB;
import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;

public class CDR_DAO {


    public static void insertCdr(Object cdr,
                                 CDREnricher.SubscriberInfo info,
                                 String sourceFile) {
        // Derive billing-compatible fields
        String dialA = getDialA(cdr);
        String dialB = getDialB(cdr);
        String startTime = getStartTime(cdr);
        long   duration  = getDuration(cdr);
        int    serviceId = getServiceId(cdr);
        String recordType = cdr.getClass().getSimpleName();

        try {
            DB.executeUpdate("""
                INSERT INTO mediation_cdr
                  (dial_a, dial_b, start_time, duration, service_id,
                   hplmn, external_charges, record_type, source_file)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT (dial_a, dial_b, start_time, duration) DO NOTHING
            """,
                    dialA, dialB, startTime, duration, serviceId,
                    info != null ? info.hplmn() : null,
                    0.00, recordType, sourceFile);
        } catch (Exception e) {
            System.err.println("CdrDao.insertCdr failed: " + e.getMessage());
        }
    }

    public static void insertAggregated(CDRAggregator.AggregationResult r,
                                        String windowType,
                                        String windowStart, String windowEnd) {
        try {
            DB.executeUpdate("""
                INSERT INTO mediation_cdr_aggregated
                  (record_type, dial_a, window_type, window_start, window_end,
                   total_records, total_duration, total_bytes, total_messages)
                VALUES (?,?,?,?,?,?,?,?,?)
            """,
                    r.recordType, r.msisdn, windowType, windowStart, windowEnd,
                    r.totalRecords, r.totalDuration, r.totalBytes, r.totalMessages);
        } catch (Exception e) {
            System.err.println("CdrDao.insertAggregated failed: " + e.getMessage());
        }
    }

    // --- Derivation helpers ---
    private static String getDialA(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callingNumber;
        if (cdr instanceof SmscCdr s)     return s.senderMSISDN;
        if (cdr instanceof PgwDataCdr p)  return p.servedMSISDN;
        return null;
    }
    private static String getDialB(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.calledNumber;
        if (cdr instanceof SmscCdr s)     return s.receiverMSISDN;
        if (cdr instanceof PgwDataCdr)    return "internet"; // billing expects this
        return null;
    }
    private static String getStartTime(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callStartTime;
        if (cdr instanceof SmscCdr s)     return s.submissionTime;
        if (cdr instanceof PgwDataCdr p)  return p.startTime;
        return null;
    }
    private static long getDuration(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callDuration; // seconds
        if (cdr instanceof SmscCdr)       return 1L;              // 1 per message
        if (cdr instanceof PgwDataCdr p)  return p.totalBytes;    // BYTES not seconds!
        return 0L;
    }
    private static int getServiceId(Object cdr) {
        if (cdr instanceof MscVoiceCdr) return 1;
        if (cdr instanceof PgwDataCdr)  return 2;
        if (cdr instanceof SmscCdr)     return 3;
        return 0;
    }
}
