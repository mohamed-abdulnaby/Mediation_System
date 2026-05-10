package processing;

import msc.MscVoiceCdr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CDREnricherTest {

    private CDREnricher enricher;

    @BeforeEach
    void setUp() {
        // This runs before every test, ensuring a fresh cache
        enricher = new CDREnricher();
    }

    @Test
    void testEnrichKnownSubscriber() {
        // 1. Create a CDR with a number that exists in your subscribers.csv
        MscVoiceCdr cdr = new MscVoiceCdr();
        cdr.callingNumber = "1234567890"; // Matches Vodafone/Cairo in roadmap seed data

        // 2. Perform enrichment
        enricher.enrich(cdr);

        // 3. Verify enrichment via lookup (since enrich currently prints to console)
        CDREnricher.SubscriberInfo info = enricher.lookup(cdr.callingNumber);

        assertEquals("Vodafone", info.carrier());
        assertEquals("Cairo", info.region());
        assertEquals("Premium", info.SubscriberType());
    }

    @Test
    void testEnrichUnknownSubscriber() {
        // 1. Create a CDR with a random number NOT in the CSV
        MscVoiceCdr cdr = new MscVoiceCdr();
        cdr.callingNumber = "9999999999";

        // 2. Perform enrichment
        enricher.enrich(cdr);

        // 3. Verify fallback values
        CDREnricher.SubscriberInfo info = enricher.lookup(cdr.callingNumber);

        assertEquals("Unknown", info.carrier());
        assertEquals("Standard", info.SubscriberType()  );
    }
}