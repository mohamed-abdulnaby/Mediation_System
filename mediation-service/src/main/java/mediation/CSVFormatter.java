package mediation;

import db.DB;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.FileWriter;

public class CSVFormatter {
    public static void format() {

        // Only select rows not yet sent to billing/fraud
        String sql = "SELECT * FROM mediation_cdr WHERE is_sent = FALSE";

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String filename = "cdr-" + date.format(formatter) + System.currentTimeMillis() / 1000 + ".csv";

        try {
            List<Map<String, Object>> rs = DB.executeSelect(sql);

            if (rs.isEmpty()) {
                System.out.println("CSVFormatter: no new records to send.");
                return;
            }

            // Write CSV header matching the billing-compatible column order
            storeInFile(
                "dial_a,dial_b,start_time,duration,service_id," +
                "hplmn,vplmn,external_charges,rejection_reason,usage_type",
                filename
            );

            // Write one row per CDR using mediation_cdr column names
            for (Map<String, Object> row : rs) {
                String line =
                        row.get("dial_a")            + "," +
                        row.get("dial_b")            + "," +
                        row.get("start_time")        + "," +
                        row.get("duration")          + "," +
                        row.get("service_id")        + "," +
                        row.get("hplmn")             + "," +
                        row.get("vplmn")             + "," +
                        row.get("external_charges")  + "," +
                        row.get("rejection_reason")  + "," +
                        row.get("usage_type");
                storeInFile(line, filename);
            }

            FileSender.sendToAll(filename);

            // Mark all sent rows so they are not included in the next flush
            DB.executeUpdate(
                "UPDATE mediation_cdr SET is_sent = TRUE, sent_at = NOW() WHERE is_sent = FALSE"
            );

            System.out.println("CSVFormatter: sent " + rs.size() + " records in " + filename);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void storeInFile(String data, String filename) {
        try (FileWriter fw = new FileWriter(filename, true)) {
            fw.write(data + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}