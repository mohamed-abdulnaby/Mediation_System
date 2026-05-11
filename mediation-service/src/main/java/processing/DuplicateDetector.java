package processing;

import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT: Prevents the same CDR from being processed twice.
 *
 * WHY:  FTP files can be downloaded more than once if the poller overlaps
 *       with a previous cycle, or if the network drops and the file is
 *       re-uploaded by the simulator. Without deduplication, a single voice
 *       call could appear twice in the billing database and the subscriber
 *       would be charged double.
 *
 * HOW:  Every CDR gets a "composite key" — a string made up of the fields
 *       that make a record unique (who called, who was called, when).
 *       That key is added to a Set. The Set only accepts each key once:
 *         - First time the key is seen  → add() returns true  → NOT a duplicate
 *         - Second time the same key appears → add() returns false → DUPLICATE
 *
 * DATA STRUCTURE CHOICE — why ConcurrentHashMap.newKeySet()?
 *   The FTP poller may run on multiple threads simultaneously (one thread
 *   per downloaded file). A plain HashSet is NOT thread-safe — two threads
 *   writing to it at the same time can corrupt it. ConcurrentHashMap.newKeySet()
 *   gives us a Set that is safe for concurrent reads and writes without
 *   needing an explicit 'synchronized' block on every call.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class DuplicateDetector {

    // Thread-safe Set backed by ConcurrentHashMap — handles concurrent FTP threads
    // without locks. Each entry is the composite key of a seen CDR.
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    /**
     * Returns true if this CDR has already been processed before.
     *
     * Pattern: Set.add() returns false when the element already exists.
     * We invert that: "not added" means "already seen" means "duplicate".
     */
    public boolean isDuplicate(Object cdr) {
        String key = buildkey(cdr);
        if (key == null) return false; // unknown CDR type — let it through
        // add() atomically checks + inserts in one step (thread-safe)
        return !seen.add(key);
    }

    /**
     * Builds a unique fingerprint string for a CDR by combining its most
     * identifying fields with a pipe separator.
     *
     * WHY pattern matching (Java 21 switch)?
     *   Instead of three separate if-instanceof blocks, the switch expression
     *   with pattern variables (e.g. 'case MscVoiceCdr msc') is cleaner and
     *   ensures the compiler warns us if we forget a CDR type.
     *
     * The prefix (MSC|, PGW|, SMS|) prevents accidental collision between
     * different CDR types that happen to share the same field values.
     */
    private String buildkey(Object cdr) {
        return switch (cdr) {
            // Voice: who called, who was called, and exactly when — all three together
            // make the call unique. Two calls between the same numbers at different
            // times will have different keys.
            case MscVoiceCdr msc -> "MSC|" + msc.callingNumber + "|" + msc.calledNumber + "|" + msc.callStartTime;

            // Data: which subscriber, on which APN (network gateway), at what time
            case PgwDataCdr pgw -> "PGW|" + pgw.servedMSISDN + "|" + pgw.apn + "|" + pgw.startTime;

            // SMS: sender, receiver, submission timestamp
            case SmscCdr sms    -> "SMS|" + sms.senderMSISDN + "|" + sms.receiverMSISDN + "|" + sms.submissionTime;

            default -> null; // unknown type — cannot deduplicate, pass through
        };
    }

    /**
     * Clears the seen-set. Call this at the start of a new processing window
     * (e.g. daily reset) to free memory. Without this the Set grows forever.
     */
    public void clear() {
        seen.clear();
    }
}
