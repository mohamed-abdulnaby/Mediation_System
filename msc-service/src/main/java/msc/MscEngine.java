package msc;

import org.telecom.common.FtpUploader;

public class MscEngine {

    private final FtpUploader uploader = new FtpUploader();

    public void start() {

        while (true) {

            try {
                // 1. generate CDR
                MscVoiceCdr cdr = MscCdrGenerator.generate();

                // 2. encode to ASN.1
                byte[] encoded = MscCdrGenerator.encode(cdr);

                // 3. build filename only (NO FILE CREATION)
                String fileName = "CDR_" + System.currentTimeMillis() + "_MSC.asn";

                // 4. upload directly
                uploader.upload(encoded, fileName);

                System.out.println("MSC sent ASN.1 binary CDR");

                Thread.sleep(5000);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public static void main(String[] args) {

        MscEngine engine = new MscEngine();

        System.out.println("MSC Service Started...");

        engine.start();
    }
}