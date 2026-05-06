package msc;

import org.telecom.ftp.FtpUploader;
import org.telecom.ftp.CdrFileWriter;

public class MscEngine {

    private final FtpUploader uploader = new FtpUploader();

    public void start() {

        while (true) {

            try {
                MscVoiceCdr cdr = MscCdrGenerator.generate();
                byte[] encoded = MscCdrGenerator.encode(cdr);

                // 3. write binary file
                String path = CdrFileWriter.write(encoded,"MSC");

                String fileName = path.substring(path.lastIndexOf("/") + 1);

                // 4. upload FTP
                uploader.upload(path, fileName);

                System.out.println("MSC sent ASN.1 binary CDR");

                Thread.sleep(3000);

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