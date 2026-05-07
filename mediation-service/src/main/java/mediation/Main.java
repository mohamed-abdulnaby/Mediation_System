package mediation;

import db.DB;
import java.sql.SQLException;
import org.telecom.common.FtpDownloader;
public class Main {
    public static void main(String[] args) {
//        System.out.println("Starting mediation process...");
//        try {
//            DB.executeSelect("SELECT 1");
//        } catch (SQLException e) {
//            System.err.println("Database connection test failed: " + e.getMessage());
//        }
//        CSVFormatter.format();
//        System.out.println("Mediation process completed.");
        FtpDownloader downloader = new FtpDownloader();

        downloader.pollAndDownload(
                ".",
                "telecom-common/CDRs/"
        );
    }
}