package processing;

import com.opencsv.CSVReader;
import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT: Enriches a raw CDR with subscriber information (carrier, region,
 *       subscriber type, HPLMN) by looking up the calling MSISDN in a
 *       pre-loaded in-memory cache.
 *
 * WHY:  A CDR coming off the FTP server only contains raw network fields —
 *       phone numbers, timestamps, durations. It has NO information about
 *       which operator the subscriber belongs to, what region they are in,
 *       or whether they are a premium/standard customer.
 *
 *       The billing system and fraud system NEED this context to:
 *         - Apply the correct tariff (carrier-specific pricing)
 *         - Detect roaming charges (HPLMN ≠ VPLMN)
 *         - Segment reports by region
 *
 * DATA SOURCE: subscribers.csv (bundled inside the JAR under resources/).
 *   Format: msisdn, carrier, region, subscriber_type, status, hplmn
 *
 * PERFORMANCE DESIGN — load once, query many:
 *   Loading a CSV file from disk on every CDR would be catastrophically slow
 *   (the system processes ~3 CDRs/second). Instead, CDREnricher reads the
 *   entire CSV exactly ONCE in its constructor and stores all rows in a
 *   HashMap<MSISDN → SubscriberInfo>. Every subsequent lookup is O(1) —
 *   a single hash table operation regardless of how large the CSV grows.
 *
 * JAVA CONCEPT — record (Java 16+):
 *   'public record SubscriberInfo(...)' is a compact, immutable data class.
 *   The compiler auto-generates the constructor, getters (carrier(), region()…),
 *   equals(), hashCode(), and toString(). No boilerplate needed.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class CDREnricher {

    /**
     * Immutable data container for a subscriber's enrichment attributes.
     *
     * WHY a record instead of a regular class?
     *   Records signal to the reader that this object is pure data — no
     *   business logic, no mutable state. It also saves ~30 lines of
     *   boilerplate (getters, equals, hashCode, toString).
     *
     * Fields:
     *   carrier        — mobile operator name (e.g. "Vodafone", "Orange")
     *   region         — geographic region of the subscriber's home cell
     *   subscriberType — tier of service: "Premium", "Standard", "Business"
     *   hplmn          — Home Public Land Mobile Network code used for roaming
     *                    detection (if HPLMN ≠ serving PLMN → subscriber is roaming)
     */
    public record SubscriberInfo(String carrier, String region, String subscriberType, String hplmn) {}

    /**
     * The in-memory lookup table.
     * Key:   MSISDN string (the subscriber's phone number)
     * Value: their enrichment data loaded from subscribers.csv
     *
     * WHY HashMap and not ConcurrentHashMap?
     *   This map is written ONCE (at startup in loadFromCsv()) and then only
     *   READ for the lifetime of the application. Read-only access from
     *   multiple threads to a HashMap is safe — concurrent writes are the
     *   only dangerous scenario.
     */
    private final Map<String, SubscriberInfo> cache = new HashMap<>();

    /**
     * Constructor — loads the CSV into the cache at startup.
     * After this returns, the cache is immutable for the rest of the run.
     */
    public CDREnricher() {
        loadFromCsv();
    }

    /**
     * Reads subscribers.csv from the JAR classpath into the in-memory cache.
     *
     * WHY getClassLoader().getResourceAsStream()?
     *   This works both when running locally in IntelliJ (reads from
     *   src/main/resources/) and inside a Docker container (reads from
     *   inside the fat JAR). Using a hardcoded file path like
     *   new File("subscribers.csv") would break in Docker.
     *
     * WHY skip(1)?
     *   The first row of the CSV is the header (column names), not data.
     *   skip(1) advances past it so we don't try to parse "msisdn" as a
     *   phone number.
     *
     * WHY row.length > 5?
     *   Defensive coding — if an older version of the CSV doesn't have the
     *   hplmn column (column index 5), we default to "UNKNOWN" instead of
     *   throwing an ArrayIndexOutOfBoundsException.
     */
    private void loadFromCsv() {
        // Loads from mediation-service/src/main/resources/subscribers.csv
        // getResourceAsStream works for local dev and the Docker JAR
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("subscribers.csv");
                CSVReader reader = new CSVReader(new InputStreamReader(is))) {
            reader.skip(1); // skip the header row: msisdn, carrier, region, subscriber_type, status, hplmn
            String[] row;
            while ((row = reader.readNext()) != null) {
                cache.put(row[0].trim(), new SubscriberInfo(
                        row[1].trim(),                              // carrier
                        row[2].trim(),                              // region
                        row[3].trim(),                              // subscriber_type
                        row.length > 5 ? row[5].trim() : "UNKNOWN" // hplmn (column may be absent in older CSV)
                ));
            }
            System.out.println("CDREnricher: Loaded " + cache.size() + " Subscribers from CSV");
        } catch (Exception e) {
            System.err.println("CDREnricher: Warning - Failed to load subscribers.csv: " + e.getMessage());
        }
    }

    /**
     * Extracts the calling MSISDN from any CDR type so we know whose
     * record to look up in the cache.
     *
     * Each CDR type stores the caller's number in a differently-named field,
     * so this helper abstracts that away. The switch pattern replaces three
     * if-instanceof blocks with a concise, exhaustive expression.
     *
     * Returns null for unknown CDR types — the caller handles null safely.
     */
    private String extractLookupKey(Object cdr) {
        return switch (cdr) {
            case MscVoiceCdr msc -> msc.callingNumber;   // voice call originator
            case SmscCdr sms     -> sms.senderMSISDN;   // SMS sender
            case PgwDataCdr pgw  -> pgw.servedMSISDN;   // data session subscriber
            case null, default   -> null;
        };
    }

    /**
     * Looks up a subscriber's enrichment data by MSISDN.
     *
     * WHY getOrDefault instead of get() + null check?
     *   getOrDefault() is a single atomic call that returns the fallback
     *   if the key is missing, making the code concise and null-safe.
     *
     * The fallback SubscriberInfo("Unknown", "Unknown", "Standard", "UNKNOWN")
     * ensures CDRs for unknown subscribers still flow through the pipeline
     * rather than crashing with a NullPointerException downstream.
     *
     * @param msisdn  the subscriber's phone number to look up
     * @return        their enrichment data, or sensible defaults if not found
     */
    public SubscriberInfo lookup(String msisdn) {
        return cache.getOrDefault(msisdn, new SubscriberInfo("Unknown", "Unknown", "Standard", "UNKNOWN"));
    }
}
