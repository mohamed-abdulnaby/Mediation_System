package processing;

import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CDRSorter {
    // Package-private: used by CDRBuffer to sort CdrEntry batches by timestamp
    String getTimestamp(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callStartTime;
        if (cdr instanceof SmscCdr s)     return s.submissionTime;
        if (cdr instanceof PgwDataCdr p)  return p.startTime;
        return "";
    }

    public List<Object> sort(List<Object> cdrs) {
        return cdrs.stream()
                .sorted(Comparator.comparing(this::getTimestamp))
                .collect(Collectors.toList());
    }
}
