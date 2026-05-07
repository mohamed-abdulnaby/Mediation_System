package smsc;

import org.telecom.common.FtpUploader;

public class SmscEngine {

    private final FtpUploader uploader = new FtpUploader();

    public void start() {

        while (true) {

            try {

                SmscCdr cdr = SmscCdrGenerator.generate();

                // 1. encode to ASN.1
                byte[] encoded = SmscCdrGenerator.encode(cdr);

                // 2. build filename only (NO FILE CREATION)
                String fileName = "CDR_" + System.currentTimeMillis() + "_SMSC.asn";

                // 3. upload directly
                uploader.upload(encoded, fileName);

                System.out.println("SMSC sent ASN.1 SMS CDR");

                Thread.sleep(3000);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {

        System.out.println("SMSC Service Started...");

        new SmscEngine().start();
    }
}