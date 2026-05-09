package processing;

import com.opencsv.CSVReader;
import msc.MscVoiceCdr;
import smsc.SmscCdr;
import pgw.PgwDataCdr;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CDREnricher {
    // record to hold the enrichment data
    public record SubscriberInfo(String carrier, String region, String SubscriberType) {}

    // The In-memory cache that will load ONLY 1 time at the startup
    private final Map<String, SubscriberInfo> cache = new HashMap<>();

    public CDREnricher(){
        loadFromCsv();
    }

    private void loadFromCsv(){
        // Loads from mediation-service/src/main/resources/subscribers.csv
        // getResourceAsStream works for local dev and the Docker JAR
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("subscribers.csv");
                CSVReader reader = new CSVReader(new InputStreamReader(is))) {
            reader.skip(1); //skips the header of the MSISDN etc...
            String[] row;
            while((row = reader.readNext()) != null){
                // msisdn, carrier, region, subscriber_type, status
                cache.put(row[0].trim(), new SubscriberInfo(row[1].trim(), row[2].trim(), row[3].trim()));
            }
            System.out.println("CDREnricher: Loaded " + cache.size() + "Subscribers from CSV");
        } catch (Exception e) {
            System.err.println("CDREnricher: Warning - Failed to load subscribers.csv: " + e.getMessage());
        }
    }

    public Object enrich(Object cdr) {
        String lookupkey = extractLookupKey(cdr);
        SubscriberInfo info = cache.getOrDefault(lookupkey, new SubscriberInfo("Unknown", "Unknown", "Standard"));
        if (cdr instanceof MscVoiceCdr msc){
            // MSC has no dedicated carrier field yet — add to CSVFormatter/DB later
            // Store enrichment as metadata (can extend MscVoiceCdr or use a wrapper)
            System.out.println("Enriched MSC CDR: carrier=" + info.carrier() + ", region=" + info.region());
        }
        // Same for SmscCdr and PgwDataCdr

        return cdr; //returns the enriched CDR
    }

    private String extractLookupKey(Object cdr) {
        return switch (cdr){
            case MscVoiceCdr msc -> msc.callingNumber;
            case SmscCdr sms -> sms.senderMSISDN;
            case PgwDataCdr pgw -> pgw.servedMSISDN;
            case null,default -> null;

        };
    }
    public SubscriberInfo lookup(String msisdn){
        return cache.getOrDefault(msisdn, new SubscriberInfo("Unknown", "Unknown", "Standard"));
    }
}
