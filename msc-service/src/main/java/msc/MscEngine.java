package msc;

import org.telecom.common.FtpUploader;

public class MscEngine {

    private final FtpUploader uploader = new FtpUploader();

    public void start() {

        while (true) {

            try {

                // 1. Generate CDR
                MscVoiceCdr cdr = MscCdrGenerator.generate();

                // 2. Encode to ASN.1
                byte[] encoded = MscCdrGenerator.encode(cdr);

                // 3. Build filename
                String fileName = "CDR_" + System.currentTimeMillis() + "_MSC.asn";

                // 4. Upload directly to FTP
                uploader.upload(encoded, fileName);

                System.out.println("MSC sent ASN.1 binary CDR: " + fileName);

            } catch (Exception e) {

                System.out.println("MSC upload failed: " + e.getMessage());
            }

            // Generate every 5 seconds
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public static void main(String[] args) {

        MscEngine engine = new MscEngine();

        System.out.println("MSC Service Started...");

        engine.start();
    }
}