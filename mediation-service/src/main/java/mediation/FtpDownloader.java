package mediation;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;

public class FtpDownloader {

    // FTP Config
    private final String host = System.getenv("FTP_HOST");
    private final String user = System.getenv("FTP_USER");
    private final String pass = System.getenv("FTP_PASS");
    private final int port = System.getenv("FTP_PORT") != null ? Integer.parseInt(System.getenv("FTP_PORT")) : 21;

    // Bounded LRU set — evicts oldest entry once size exceeds 10,000.
    // Fixes unbounded HashSet growth that accumulated every filename forever.
    private final Set<String> processedFiles = Collections.newSetFromMap(
        new LinkedHashMap<String, Boolean>() {
            protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> e) {
                return size() > 10_000;
            }
        }
    );

    // Persistent FTP connection — reused across poll cycles.
    // Fixes reconnecting (TCP handshake + FTP login) on every 5-second poll.
    private FTPClient ftp = null;

    // Decode + Filter Processor
    private final FtpProcessor processor = new FtpProcessor();

    private FTPClient connect() throws Exception {
        FTPClient client = new FTPClient();
        client.connect(host, port);
        client.login(user, pass);
        client.enterLocalPassiveMode();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        return client;
    }

    // Reuses the existing connection if healthy; reconnects only when needed.
    private boolean ensureConnected() {
        try {
            if (ftp != null && ftp.isConnected() && ftp.sendNoOp()) return true;
        } catch (Exception ignored) {}
        try {
            ftp = connect();
            System.out.println("[FtpDownloader] Connected to FTP server: " + host);
            return true;
        } catch (Exception e) {
            System.err.println("[FtpDownloader] Reconnect failed: " + e.getMessage());
            ftp = null;
            return false;
        }
    }

    public void pollFromFtp() {

        while (true) {

            if (!ensureConnected()) {
                sleep();
                continue;
            }

            try {

                String remoteDir = ".";
                String[] files = ftp.listNames(remoteDir);

                if (files != null) {

                    for (String file : files) {

                        file = new java.io.File(file).getName();

                        // Only ASN files
                        if (!file.endsWith(".asn")) {
                            continue;
                        }

                        // Skip already-processed files
                        if (processedFiles.contains(file)) {
                            continue;
                        }

                        System.out.println("[FtpDownloader] New file detected: " + file);

                        String remotePath = remoteDir + "/" + file;

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        boolean success = ftp.retrieveFile(remotePath, baos);

                        if (success) {
                            byte[] data = baos.toByteArray();
                            processedFiles.add(file);
                            processor.process(data);
                            System.out.println("[FtpDownloader] Downloaded: " + file);
                        } else {
                            System.err.println("[FtpDownloader] Failed to download: " + file);
                        }
                    }
                }

            } catch (Exception e) {
                System.err.println("[FtpDownloader] Poll error: " + e.getMessage());
                ftp = null; // force reconnect on next cycle
            }

            // Poll every 5 seconds
            sleep();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}