package org.telecom.common;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;

public class FtpUploader {

    private final String host = "localhost";
    private final int port = 21;
    private final String user = "testuser";
    private final String pass = "testpass";

    public void upload(byte[] data,String fileName) {

        FTPClient ftp = new FTPClient();

        try (InputStream input = new ByteArrayInputStream(data)) {

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
            ftp.changeWorkingDirectory("/home/testuser");
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