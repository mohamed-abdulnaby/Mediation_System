package pgw;

import org.telecom.common.FtpUploader;

public class PgwEngine {

    private final FtpUploader uploader = new FtpUploader();

    public void start() {

        while (true) {

            try {

                PgwDataCdr cdr = PgwCdrGenerator.generate();

                // 2. encode to ASN.1
                byte[] encoded = PgwCdrGenerator.encode(cdr);

                // 3. build filename only (NO FILE CREATION)
                String fileName = "CDR_" + System.currentTimeMillis() + "_PGW.asn";

                // 4. upload directly
                uploader.upload(encoded, fileName);

                System.out.println("PGW sent ASN.1 Data CDR");

            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
        }
    }
    public static void main(String[] args) {

        PgwEngine engine = new PgwEngine();

        System.out.println("PGW Service Started...");

        engine.start();
    }
}
