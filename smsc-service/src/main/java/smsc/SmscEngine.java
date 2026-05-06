package smsc;

import org.telecom.ftp.FtpUploader;
import org.telecom.ftp.CdrFileWriter;
public class SmscEngine {

    private final FtpUploader uploader = new FtpUploader();

    public void start() {

        while (true) {

            try {

                SmscCdr cdr = SmscCdrGenerator.generate();

                byte[] encoded = SmscCdrGenerator.encode(cdr);

                String path = CdrFileWriter.write(encoded,"SMSC");

                String fileName = path.substring(path.lastIndexOf("/") + 1);

                uploader.upload(path, fileName);

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