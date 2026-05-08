package mediation;
import msc.MscVoiceCdr;
import org.bouncycastle.asn1.*;
import pgw.PgwDataCdr;
import smsc.SmscCdr;

public class Decoder {
    public Object decode(byte[] data) throws Exception {

        ASN1Sequence seq = (ASN1Sequence) ASN1Sequence.fromByteArray(data);

        String type = seq.getObjectAt(0).toString();

        return switch (type) {
            case "MSC_VOICE" -> decodeMsc(seq);
            case "SMSC_SMS" -> decodeSms(seq);
            case "PGW_DATA" -> decodePgw(seq);
            default -> throw new RuntimeException("Unknown CDR type");
        };
    }

    private MscVoiceCdr decodeMsc(ASN1Sequence seq) {

        MscVoiceCdr cdr = new MscVoiceCdr();

        cdr.recordType = seq.getObjectAt(0).toString();
        cdr.servedIMSI = seq.getObjectAt(1).toString();
        cdr.servedMSISDN = seq.getObjectAt(2).toString();

        cdr.callingNumber = seq.getObjectAt(3).toString();
        cdr.calledNumber = seq.getObjectAt(4).toString();

        cdr.callStartTime = seq.getObjectAt(5).toString();
        cdr.callEndTime = seq.getObjectAt(6).toString();

        cdr.callDuration =
                Long.parseLong(seq.getObjectAt(7).toString());

        cdr.cellId = seq.getObjectAt(8).toString();
        cdr.locationAreaCode = seq.getObjectAt(9).toString();

        cdr.terminationCause = seq.getObjectAt(10).toString();

        return cdr;
    }

    private PgwDataCdr decodePgw(ASN1Sequence seq) {

        PgwDataCdr cdr = new PgwDataCdr();

        cdr.recordType = seq.getObjectAt(0).toString();
        cdr.servedIMSI = seq.getObjectAt(1).toString();
        cdr.servedMSISDN = seq.getObjectAt(2).toString();

        cdr.apn = seq.getObjectAt(3).toString();
        cdr.pdpType = seq.getObjectAt(4).toString();

        cdr.startTime = seq.getObjectAt(5).toString();
        cdr.endTime = seq.getObjectAt(6).toString();

        cdr.sessionDuration =
                Long.parseLong(seq.getObjectAt(7).toString());

        cdr.uplinkBytes =
                Long.parseLong(seq.getObjectAt(8).toString());

        cdr.downlinkBytes =
                Long.parseLong(seq.getObjectAt(9).toString());

        cdr.totalBytes =
                Long.parseLong(seq.getObjectAt(10).toString());

        cdr.ipAddress = seq.getObjectAt(11).toString();
        cdr.cellId = seq.getObjectAt(12).toString();
        cdr.locationAreaCode = seq.getObjectAt(13).toString();

        cdr.qosClassIdentifier =
                Integer.parseInt(seq.getObjectAt(14).toString());

        cdr.terminationCause = seq.getObjectAt(15).toString();

        return cdr;
    }
    private SmscCdr decodeSms(ASN1Sequence seq) {

        SmscCdr cdr = new SmscCdr();

        cdr.recordType = seq.getObjectAt(0).toString();
        cdr.senderIMSI = seq.getObjectAt(1).toString();
        cdr.senderMSISDN = seq.getObjectAt(2).toString();

        cdr.receiverMSISDN = seq.getObjectAt(3).toString();
        cdr.messageType = seq.getObjectAt(4).toString();

        cdr.submissionTime = seq.getObjectAt(5).toString();
        cdr.deliveryTime = seq.getObjectAt(6).toString();

        cdr.status = seq.getObjectAt(7).toString();
        cdr.messageSize = seq.getObjectAt(8).toString();

        cdr.cellId = seq.getObjectAt(9).toString();

        return cdr;
    }
}

