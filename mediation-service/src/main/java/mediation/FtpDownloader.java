package mediation;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

public class FtpDownloader {

    // FTP Config
    private final String host = System.getenv("FTP_HOST");
    private final String user = System.getenv("FTP_USER");
    private final String pass = System.getenv("FTP_PASS");
    private final int port = 21;
    // processed files tracker
    private final Set<String> processedFiles = new HashSet<>();

    // Decode + Filter
    private final FtpProcessor processor = new FtpProcessor();

    private FTPClient connect() throws Exception {

        FTPClient ftp = new FTPClient();

        ftp.connect(host, port);
        ftp.login(user, pass);

        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);

        return ftp;
    }

    public void pollFromFtp() {

        while (true) {

            try {

                FTPClient ftp = connect();
                String remoteDir=".";
                String[] files = ftp.listNames(remoteDir);

                if (files != null) {

                    for (String file : files) {
                        file = new java.io.File(file).getName();
                        // only ASN files
                        if (!file.endsWith(".asn")) {
                            continue;
                        }

                        // skip old files
                        if (processedFiles.contains(file)) {
                            continue;
                        }

                        System.out.println("New file detected: " + file);

                        String remotePath = remoteDir + "/" + file;

                        // Convert File To Output Stream
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();

                        boolean success = ftp.retrieveFile(remotePath, baos);


                        if (success) {

                            byte[] data = baos.toByteArray();
                            processedFiles.add(file);

                            processor.process(data);
                            System.out.println("Downloaded: " + file);

                        } else {

                            System.out.println("Failed to download: " + file);
                        }

                    }
                }

                ftp.logout();
                ftp.disconnect();

                Thread.sleep(3000);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}