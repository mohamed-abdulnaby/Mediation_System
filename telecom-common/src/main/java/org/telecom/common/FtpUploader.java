package org.telecom.common;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class FtpUploader {

    private final String host = System.getenv("FTP_HOST");
    private final String user = System.getenv("FTP_USER");
    private final String pass = System.getenv("FTP_PASS");
    private final int port = 21;

    public void upload(byte[] data, String fileName) {

        while (true) {

            FTPClient ftp = new FTPClient();

            try (InputStream input = new ByteArrayInputStream(data)) {

                ftp.connect(host, port);

                if (!ftp.login(user, pass)) {
                    ftp.logout();
                    throw new RuntimeException("FTP login failed");
                }

                ftp.enterLocalPassiveMode();
                ftp.setFileType(FTP.BINARY_FILE_TYPE);
                ftp.changeWorkingDirectory("/home/testuser");

                boolean uploaded = ftp.storeFile(fileName, input);

                if (uploaded) {
                    System.out.println("Uploaded: " + fileName);
                } else {
                    System.out.println("Upload failed: " + fileName);
                }

                ftp.logout();
                ftp.disconnect();

                break;
            } catch (Exception e) {

                System.out.println("FTP not ready yet... retrying (" + e.getMessage() + ")");

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}

            }
        }
    }
}