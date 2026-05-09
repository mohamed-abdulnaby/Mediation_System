package processing;

import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DuplicateDetector {
    // Thread-safe: Multiple FTP threads may call this simultaneously
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public boolean isDupliacte(Object cdr) {
        String key = buildkey(cdr);
        if (key == null) return false;
        //add() returns false if key already exists (Duplicate in this case)
        return !seen.add(key);
    }


    private String buildkey(Object cdr) {
        return switch (cdr) {
            case MscVoiceCdr msc -> "MSC|" + msc.callingNumber + "|" + msc.calledNumber + "|" + msc.callStartTime;
            case PgwDataCdr pgw -> "PGW|" + pgw.servedMSISDN + "|" + pgw.apn + "|" + pgw.startTime;
            case SmscCdr sms -> "SMS|" + sms.senderMSISDN + "|" + sms.receiverMSISDN + "|" + sms.submissionTime;
            default -> null;

        };
    }

    public void clear() {
        seen.clear();
    }
}


