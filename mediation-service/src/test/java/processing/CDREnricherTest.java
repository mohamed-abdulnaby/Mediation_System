package processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CDREnricherTest {

    private CDREnricher enricher;

    @BeforeEach
    void setUp() {
        // Loads subscribers.csv once into the in-memory cache
        enricher = new CDREnricher();
    }

    @Test
    void testLookupKnownSubscriber() {
        // lookup() returns enrichment data for a known MSISDN from subscribers.csv
        // "1234567890" is expected to map to Vodafone / Cairo / Premium in the seed data
        CDREnricher.SubscriberInfo info = enricher.lookup("1234567890");

        assertEquals("Vodafone",  info.carrier());
        assertEquals("Cairo",     info.region());
        assertEquals("Premium",   info.subscriberType()); // record accessor — lowercase s
    }

    @Test
    void testLookupUnknownSubscriber() {
        // For any MSISDN not in the CSV, lookup() returns the fallback SubscriberInfo
        CDREnricher.SubscriberInfo info = enricher.lookup("9999999999");

        assertEquals("Unknown",  info.carrier());
        assertEquals("Unknown",  info.region());
        assertEquals("Standard", info.subscriberType()); // fallback default
        assertEquals("UNKNOWN",  info.hplmn());
    }
}