package pgw;

import java.time.LocalDateTime;
import java.util.Random;
import org.bouncycastle.asn1.*;

public class PgwCdrGenerator {

    private static final Random r = new Random();

    public static PgwDataCdr generate() {

        PgwDataCdr cdr = new PgwDataCdr();

        cdr.servedIMSI = "60201" + (100000000 + r.nextInt(900000000));
        cdr.servedMSISDN = "20" + (1000000000 + r.nextInt(900000000));

        cdr.apn = "internet";
        cdr.pdpType = "IPv4";

        LocalDateTime start = LocalDateTime.now().minusSeconds(r.nextInt(600));
        LocalDateTime end = start.plusSeconds(r.nextInt(600));

        cdr.startTime = start.toString();
        cdr.endTime = end.toString();

        cdr.sessionDuration = java.time.Duration.between(start, end).getSeconds();

        cdr.uplinkBytes = r.nextInt(500) * 1024L;
        cdr.downlinkBytes = r.nextInt(2000) * 1024L;
        cdr.totalBytes = cdr.uplinkBytes + cdr.downlinkBytes;

        cdr.ipAddress = "10." + r.nextInt(255) + "." + r.nextInt(255) + "." + r.nextInt(255);

        cdr.cellId = "CELL-" + r.nextInt(9999);
        cdr.locationAreaCode = "LAC-" + r.nextInt(999);

        cdr.qosClassIdentifier = r.nextInt(9) + 1;

        cdr.terminationCause = r.nextBoolean() ? "NORMAL" : "TIMEOUT";

        return cdr;
    }
    public static byte[] encode(PgwDataCdr cdr) throws Exception {

        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new DERIA5String(cdr.recordType));
        v.add(new DERIA5String(cdr.servedIMSI));
        v.add(new DERIA5String(cdr.servedMSISDN));
        v.add(new DERIA5String(cdr.apn));
        v.add(new DERIA5String(cdr.pdpType));

        v.add(new DERIA5String(cdr.startTime));
        v.add(new DERIA5String(cdr.endTime));

        v.add(new ASN1Integer(cdr.sessionDuration));

        v.add(new ASN1Integer(cdr.uplinkBytes));
        v.add(new ASN1Integer(cdr.downlinkBytes));
        v.add(new ASN1Integer(cdr.totalBytes));

        v.add(new DERIA5String(cdr.ipAddress));
        v.add(new DERIA5String(cdr.cellId));
        v.add(new DERIA5String(cdr.locationAreaCode));

        v.add(new ASN1Integer(cdr.qosClassIdentifier));

        v.add(new DERIA5String(cdr.terminationCause));

        return new DERSequence(v).getEncoded();
    }
}