package smsc;

import java.time.LocalDateTime;
import java.util.Random;
import org.bouncycastle.asn1.*;

public class SmscCdrGenerator {

    private static final Random r = new Random();

    public static SmscCdr generate() {

        SmscCdr cdr = new SmscCdr();

        cdr.senderIMSI = "60201" + (100000000 + r.nextInt(900000000));
        cdr.senderMSISDN = "20" + (1000000000 + r.nextInt(900000000));
        cdr.receiverMSISDN = "20" + (1000000000 + r.nextInt(900000000));

        cdr.messageType = r.nextBoolean() ? "MO" : "MT";

        LocalDateTime submit = LocalDateTime.now().minusSeconds(r.nextInt(300));
        LocalDateTime deliver = submit.plusSeconds(r.nextInt(60));

        cdr.submissionTime = submit.toString();
        cdr.deliveryTime = deliver.toString();

        cdr.status = r.nextBoolean() ? "DELIVERED" : "FAILED";

        cdr.messageSize = String.valueOf(20 + r.nextInt(160));

        cdr.cellId = "CELL-" + r.nextInt(9999);

        return cdr;
    }
    public static byte[] encode(SmscCdr cdr) throws Exception {

        ASN1EncodableVector v = new ASN1EncodableVector();

        v.add(new DERIA5String(cdr.recordType));
        v.add(new DERIA5String(cdr.senderIMSI));
        v.add(new DERIA5String(cdr.senderMSISDN));
        v.add(new DERIA5String(cdr.receiverMSISDN));

        v.add(new DERIA5String(cdr.messageType));
        v.add(new DERIA5String(cdr.submissionTime));
        v.add(new DERIA5String(cdr.deliveryTime));

        v.add(new DERIA5String(cdr.status));
        v.add(new DERIA5String(cdr.messageSize));

        v.add(new DERIA5String(cdr.cellId));

        return new DERSequence(v).getEncoded();
    }
}