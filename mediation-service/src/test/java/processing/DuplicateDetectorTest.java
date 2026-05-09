package processing;

import msc.MscVoiceCdr;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectorTest {
    @Test
    void testSameCdrIsDetectedAsDuplicate() {
        MscVoiceCdr cdr = new MscVoiceCdr();
        cdr.callingNumber="1234567890";
        cdr.calledNumber="0987654321";
        cdr.callStartTime="2024-01-15T09:30:00";

        DuplicateDetector detector = new DuplicateDetector();
        assertFalse(detector.isDupliacte(cdr),"First appearance should not be a duplicate");
        assertTrue(detector.isDupliacte(cdr), "Second appearance of the same CDR must be a duplicate");
    }
}
