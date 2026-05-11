package processing;

import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT: Sorts a batch of CDRs in chronological order before they are written
 *       to the database.
 *
 * WHY:  CDRs arrive from three different simulators (MSC, SMSC, PGW) that each
 *       run on their own thread and upload files at slightly different times.
 *       When CDRBuffer drains its queue, the records are in arrival order —
 *       not necessarily in the order the events actually happened.
 *
 *       Sorting before insert ensures:
 *         1. The billing system sees records in the correct chronological order,
 *            which is required for session reconstruction and rated billing.
 *         2. Database indexes on start_time stay well-ordered, improving
 *            query performance (sequential writes vs random writes).
 *
 * HOW:  Each CDR type (MscVoiceCdr, SmscCdr, PgwDataCdr) stores its timestamp
 *       in a differently-named field. getTimestamp() abstracts that difference
 *       so sort() can work on any CDR type with a single Comparator.
 *
 * JAVA CONCEPT — Streams + Comparator.comparing():
 *   cdrs.stream()
 *       .sorted(Comparator.comparing(this::getTimestamp))
 *   This is a functional pipeline: we create a stream from the list,
 *   attach a sort step using the timestamp extractor as a key function,
 *   and collect back into a List. No manual loop or temporary array needed.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class CDRSorter {

    /**
     * Extracts the timestamp string from any CDR type.
     *
     * Package-private (no modifier) — only CDRBuffer in the same package
     * calls this directly. External code uses sort() instead.
     *
     * WHY instanceof pattern matching?
     *   Each CDR class was written independently (by Person A) with different
     *   field names. We can't add a common interface to them without modifying
     *   their source, so we use 'instanceof' to handle each case.
     */
    String getTimestamp(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callStartTime;   // e.g. "20240510143022"
        if (cdr instanceof SmscCdr s)     return s.submissionTime;  // same format
        if (cdr instanceof PgwDataCdr p)  return p.startTime;       // same format
        return ""; // unknown type — sort to front (safe default)
    }

    /**
     * Returns a new list containing all CDRs sorted by their timestamp
     * in ascending (oldest-first) order.
     *
     * The original list is not modified — we produce a new sorted list
     * via the stream pipeline.
     *
     * @param cdrs  mixed list of MscVoiceCdr / SmscCdr / PgwDataCdr objects
     * @return      a new List sorted chronologically by each record's timestamp
     */
    public List<Object> sort(List<Object> cdrs) {
        return cdrs.stream()
                .sorted(Comparator.comparing(this::getTimestamp)) // sort key = timestamp string
                .collect(Collectors.toList());
    }
}
