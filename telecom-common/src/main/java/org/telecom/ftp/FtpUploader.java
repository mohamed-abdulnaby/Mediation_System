package org.telecom.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.IOException;

public class FtpUploader {

    private final String host = "localhost";
    private final int port = 21;
    private final String user = "ftpuser";
    private final String pass = "ftp123";

    public void upload(String filePath, String fileName) {

        FTPClient ftp = new FTPClient();

        try (InputStream input = new FileInputStream(new File(filePath))) {

            ftp.connect(host, port);

            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                ftp.disconnect();
                throw new RuntimeException("FTP server refused connection.");
            }

            boolean login = ftp.login(user, pass);

            if (!login) {
                ftp.logout();
                throw new RuntimeException("FTP login failed.");
            }

            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            boolean uploaded = ftp.storeFile(fileName, input);

            if (uploaded) {
                System.out.println("Uploaded: " + fileName);
            } else {
                System.out.println("Upload failed: " + fileName);
            }

            ftp.logout();
            ftp.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}