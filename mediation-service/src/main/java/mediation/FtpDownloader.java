package mediation;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.Set;

public class FtpDownloader {

    // FTP Config
    private final String host = System.getenv("FTP_HOST");
    private final String user = System.getenv("FTP_USER");
    private final String pass = System.getenv("FTP_PASS");
    private final int port = 21;

    // Processed files tracker
    private final Set<String> processedFiles = new HashSet<>();

    // Decode + Filter Processor
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

            FTPClient ftp = null;

            try {

                ftp = connect();

                String remoteDir = ".";
                String[] files = ftp.listNames(remoteDir);

                if (files != null) {

                    for (String file : files) {

                        file = new java.io.File(file).getName();

                        // Only ASN files
                        if (!file.endsWith(".asn")) {
                            continue;
                        }

                        // Skip processed files
                        if (processedFiles.contains(file)) {
                            continue;
                        }

                        System.out.println("New file detected: " + file);

                        String remotePath = remoteDir + "/" + file;

                        // Download file into memory
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();

                        boolean success = ftp.retrieveFile(remotePath, baos);

                        if (success) {

                            byte[] data = baos.toByteArray();

                            processedFiles.add(file);

                            // Decode + Filter
                            processor.process(data);

                            System.out.println("Downloaded: " + file);

                        } else {

                            System.out.println("Failed to download: " + file);
                        }
                    }
                }

            } catch (Exception e) {

                System.out.println("FTP connection failed: " + e.getMessage());

            } finally {

                try {

                    if (ftp != null && ftp.isConnected()) {

                        ftp.logout();
                        ftp.disconnect();
                    }

                } catch (Exception ignored) {
                }
            }

            // Poll every 3 seconds
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
        }
    }
}