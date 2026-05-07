package org.telecom.common;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

public class FtpDownloader {

    // FTP Config
    private final String host = "localhost";
    private final int port = 21;
    private final String user = "testuser";
    private final String pass = "testpass";

    // processed files tracker
    private final Set<String> processedFiles = new HashSet<>();

    private FTPClient connect() throws Exception {

        FTPClient ftp = new FTPClient();

        ftp.connect(host, port);
        ftp.login(user, pass);

        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);

        return ftp;
    }

    public void pollAndDownload(String remoteDir, String localDir) {

        while (true) {

            try {

                FTPClient ftp = connect();

                String[] files = ftp.listNames(remoteDir);
                System.out.println("FILES:");

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
                        String localPath = localDir + "/" + file;

                        try (OutputStream os = new FileOutputStream(localPath)) {

                            boolean success = ftp.retrieveFile(remotePath, os);

                            if (success) {

                                processedFiles.add(file);

                                System.out.println("Downloaded: " + file);

                            } else {

                                System.out.println("Failed to download: " + file);
                            }
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