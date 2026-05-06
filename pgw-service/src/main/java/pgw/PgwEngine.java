package pgw;

import org.telecom.ftp.FtpUploader;
import org.telecom.ftp.CdrFileWriter;
public class PgwEngine {

    private final FtpUploader uploader = new FtpUploader();

    public void start() {

        while (true) {

            try {

                PgwDataCdr cdr = PgwCdrGenerator.generate();

                byte[] encoded = PgwCdrGenerator.encode(cdr);

                String path = CdrFileWriter.write(encoded,"PGW");

                String fileName = path.substring(path.lastIndexOf("/") + 1);

                uploader.upload(path, fileName);

                System.out.println("PGW sent ASN.1 Data CDR");

                Thread.sleep(3000);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public static void main(String[] args) {

        PgwEngine engine = new PgwEngine();

        System.out.println("PGW Service Started...");

        engine.start();
    }
}
