package msc;

import java.time.LocalDateTime;
import java.util.Random;

import org.bouncycastle.asn1.*;

public class MscCdrGenerator {

    private static final Random r = new Random();

    public static MscVoiceCdr generate() {

        MscVoiceCdr cdr = new MscVoiceCdr();

        cdr.servedIMSI = "60201" + (100000000 + r.nextInt(900000000));
        cdr.servedMSISDN = "20" + (1000000000 + r.nextInt(900000000));

        cdr.callingNumber = cdr.servedMSISDN;
        cdr.calledNumber = "20" + (1000000000 + r.nextInt(900000000));

        LocalDateTime start = LocalDateTime.now().minusSeconds(r.nextInt(300));
        LocalDateTime end = start.plusSeconds(r.nextInt(300));

        cdr.callStartTime = start.toString();
        cdr.callEndTime = end.toString();

        cdr.callDuration = java.time.Duration.between(start, end).getSeconds();

        cdr.cellId = "CELL-" + r.nextInt(9999);
        cdr.locationAreaCode = "LAC-" + r.nextInt(999);

        cdr.terminationCause = r.nextBoolean() ? "NORMAL" : "BUSY";

        return cdr;
    }
    public static byte[] encode(MscVoiceCdr cdr) throws Exception {

        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new DERIA5String(cdr.recordType));
        v.add(new DERIA5String(cdr.servedIMSI));
        v.add(new DERIA5String(cdr.servedMSISDN));
        v.add(new DERIA5String(cdr.callingNumber));
        v.add(new DERIA5String(cdr.calledNumber));
        v.add(new DERIA5String(cdr.callStartTime));
        v.add(new DERIA5String(cdr.callEndTime));
        v.add(new ASN1Integer(cdr.callDuration));
        v.add(new DERIA5String(cdr.cellId));
        v.add(new DERIA5String(cdr.locationAreaCode));
        v.add(new DERIA5String(cdr.terminationCause));

        DERSequence sequence = new DERSequence(v);

        return sequence.getEncoded();
    }

}