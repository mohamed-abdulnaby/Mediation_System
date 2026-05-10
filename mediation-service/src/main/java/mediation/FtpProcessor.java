package mediation;

import msc.MscVoiceCdr;
import pgw.PgwDataCdr;
import processing.CDRAggregator;
import processing.CDRBuffer;
import processing.CDREnricher;
import processing.DuplicateDetector;
import smsc.SmscCdr;

public class FtpProcessor {

    private final Decoder decoder = new Decoder();
    private final DuplicateDetector dedup      = new DuplicateDetector();
    private final CDREnricher       enricher   = new CDREnricher(); // loads CSV once
    private final CDRAggregator aggregator = new CDRAggregator();
    private final CDRBuffer buffer     = new CDRBuffer(
            () -> mediation.CSVFormatter.format() // Person C's trigger
    );

    public void process(byte[] data) {

        try {

            // 1. Decode
            Object cdr = decoder.decode(data);

            // 2. Filter
            if (!isValid(cdr)) {

                System.out.println("REJECTED CDR");
                return;
            }
            if (dedup.isDuplicate(cdr)) return;                      // dedup
            CDREnricher.SubscriberInfo info = enricher.lookup(getDialA(cdr)); // enrich — pass msisdn String
            aggregator.aggregate(cdr, info.hplmn());                  // aggregate
            buffer.add(cdr);                                          // buffer → sorts → DB → CSV

            // 3. Output
            //printCdr(cdr);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= FILTER =================

    private boolean isValid(Object cdr) {

        // MSC FILTER
        if (cdr instanceof MscVoiceCdr msc) {

            if (msc.callDuration <= 3) {
                return false;
            }

            if (msc.callingNumber == null ||
                    msc.calledNumber == null) {
                return false;
            }

            return true;
        }

        // SMSC FILTER
        if (cdr instanceof SmscCdr sms) {

            if (!"DELIVERED".equals(sms.status)) {
                return false;
            }

            if (sms.messageSize == null ||
                    sms.messageSize.isEmpty()) {
                return false;
            }

            return true;
        }

        // PGW FILTER
        if (cdr instanceof PgwDataCdr pgw) {

            if (pgw.totalBytes <= 0) {
                return false;
            }

            if (pgw.sessionDuration <= 0) {
                return false;
            }

            return true;
        }

        return false;
    }

    // Extracts dial_a (calling MSISDN) from any CDR type — used for enrichment lookup
    private String getDialA(Object cdr) {
        if (cdr instanceof MscVoiceCdr m) return m.callingNumber;
        if (cdr instanceof SmscCdr s)     return s.senderMSISDN;
        if (cdr instanceof PgwDataCdr p)  return p.servedMSISDN;
        return null;
    }

    // ================= OUTPUT =================

//    private void printCdr(Object cdr) {
//
//        System.out.println("\n========== CLEAN CDR ==========");
//
//        // MSC
//        if (cdr instanceof MscVoiceCdr msc) {
//
//            System.out.println("TYPE: MSC_VOICE");
//            System.out.println("CALLING: " + msc.callingNumber);
//            System.out.println("CALLED: " + msc.calledNumber);
//            System.out.println("DURATION: " + msc.callDuration);
//        }
//
//        // SMSC
//        else if (cdr instanceof SmscCdr sms) {
//
//            System.out.println("TYPE: SMSC_SMS");
//            System.out.println("SENDER: " + sms.senderMSISDN);
//            System.out.println("RECEIVER: " + sms.receiverMSISDN);
//            System.out.println("STATUS: " + sms.status);
//        }
//
//        // PGW
//        else if (cdr instanceof PgwDataCdr pgw) {
//
//            System.out.println("TYPE: PGW_DATA");
//            System.out.println("IMSI: " + pgw.servedIMSI);
//            System.out.println("TOTAL BYTES: " + pgw.totalBytes);
//            System.out.println("DURATION: " + pgw.sessionDuration);
//        }
//
//        System.out.println("================================\n");
//    }
}