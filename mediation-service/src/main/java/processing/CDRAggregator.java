package processing;

import msc.MscVoiceCdr;

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
        // Same pattern for SmscCdr (totalMessages++) and PgwDataCdr (totalBytes += p.totalBytes)
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
        // CdrDao.insertAggregated(...) for each entry then clear
        hourly.clear();
        daily.clear();
    }
}
