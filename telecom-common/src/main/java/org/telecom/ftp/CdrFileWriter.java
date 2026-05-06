package org.telecom.ftp;

import java.io.FileOutputStream;

public class CdrFileWriter {

    public static String write(byte[] data, String prefix) throws Exception {

        String fileName = "CDR_" + System.currentTimeMillis() + "_" + prefix + ".asn";
        String path = "telecom-common/CDRs/" + fileName;

        FileOutputStream fos = new FileOutputStream(path);
        fos.write(data);
        fos.close();

        return path;
    }
}