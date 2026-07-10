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
            () -> mediation.CSVFormatter.format(), // trigger
            aggregator                              // so buffer can flush aggregation to DB
    );

    public void process(byte[] data, String sourceFile) {

        try {

            // 1. Decode
            Object cdr = decoder.decode(data);

            // 2. Filter / Validate
            String rejectionReason = getRejectionReason(cdr);
            if (rejectionReason != null) {
                System.out.println("REJECTED CDR: " + rejectionReason);
                CDREnricher.SubscriberInfo info = new CDREnricher.SubscriberInfo("Unknown", "Unknown", "Standard", "UNKNOWN");
                String dialA = getDialA(cdr);
                if (dialA != null) {
                    info = enricher.lookup(dialA);
                }
                processing.CDR_DAO.insertCdr(cdr, info, sourceFile, rejectionReason);
                return;
            }
            if (dedup.isDuplicate(cdr)) return;                      // dedup
            CDREnricher.SubscriberInfo info = enricher.lookup(getDialA(cdr)); // enrich — pass msisdn String
            aggregator.aggregate(cdr, info.hplmn());                  // aggregate
            buffer.add(cdr, info, sourceFile);                        // buffer → sorts → DB → CSV

            // 3. Output
            //printCdr(cdr);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= FILTER & AUDIT =================

    private String getRejectionReason(Object cdr) {
        if (cdr == null) {
            return "Failed to decode CDR structure";
        }

        // MSC FILTER
        if (cdr instanceof MscVoiceCdr msc) {
            if (msc.callingNumber == null || msc.calledNumber == null) {
                return "Missing calling or called phone number";
            }
            if (msc.callDuration <= 3) {
                return "Voice call duration ≤ 3s (short call)";
            }
            return null;
        }

        // SMSC FILTER
        if (cdr instanceof SmscCdr sms) {
            if (!"DELIVERED".equals(sms.status)) {
                return "SMS transmission failed (Status: " + sms.status + ")";
            }
            if (sms.messageSize == null || sms.messageSize.isEmpty()) {
                return "Empty SMS message payload";
            }
            return null;
        }

        // PGW FILTER
        if (cdr instanceof PgwDataCdr pgw) {
            if (pgw.totalBytes <= 0) {
                return "Empty mobile data session (bytes = 0)";
            }
            if (pgw.sessionDuration <= 0) {
                return "Invalid data session duration (seconds ≤ 0)";
            }
            return null;
        }

        return "Unknown CDR record type: " + cdr.getClass().getSimpleName();
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