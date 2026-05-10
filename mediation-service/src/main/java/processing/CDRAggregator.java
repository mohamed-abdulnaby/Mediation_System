package processing;

import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CDRAggregator {

    public static class AggregationResult {
        public final String recordType, msisdn;
        public volatile int  totalRecords  = 0;
        public volatile long totalDuration = 0; // MSC
        public volatile long totalBytes    = 0; // PGW
        public volatile int  totalMessages = 0; // SMSC

        public AggregationResult(String recordType, String msisdn) {
            this.recordType = recordType;
            this.msisdn = msisdn;
        }
    }

    private final Map<String, AggregationResult> hourly = new ConcurrentHashMap<>();
    private final Map<String, AggregationResult> daily  = new ConcurrentHashMap<>();

    public void aggregate(Object cdr, String hplmn) {
        if (cdr instanceof MscVoiceCdr m) {
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

    private void update(Map<String, AggregationResult> map, String key,
                        String msisdn, String type,
                        java.util.function.Consumer<AggregationResult> updater) {
        AggregationResult r = map.computeIfAbsent(key, k -> new AggregationResult(type, msisdn));
        synchronized (r) { updater.accept(r); }
    }

    private String bucket(String timestamp, String type) {
        // Truncate to hour (H) or day (D) — adjust parsing to match your timestamp format
        try {
            return type.equals("H") ? timestamp.substring(0, 10) : timestamp.substring(0, 8);
        } catch (Exception e) { return timestamp; }
    }

    public Map<String, AggregationResult> getHourly() { return hourly; }
    public Map<String, AggregationResult> getDaily()  { return daily; }

    public void flushToDB() {
        hourly.forEach((key, r) -> {
            String[] parts = key.split("\\|");
            String windowStart = parts.length > 3 ? parts[3] : "";
            CDR_DAO.insertAggregated(r, "HOURLY", windowStart, windowStart + ":59:59");
        });
        daily.forEach((key, r) -> {
            String[] parts = key.split("\\|");
            String windowStart = parts.length > 3 ? parts[3] : "";
            CDR_DAO.insertAggregated(r, "DAILY", windowStart, windowStart + "T23:59:59");
        });
        hourly.clear();
        daily.clear();
    }
}
