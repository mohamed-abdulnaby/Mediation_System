package mediation;

import java.sql.ResultSet;
import java.io.File;

public class CSVFormatter {
    public static void format() {
        DatabaseHandler dbHandler = new DatabaseHandler();
        dbHandler.connect();
        String sql = "SELECT * FROM cdr";
        ResultSet rs = dbHandler.executeQuery(sql);
        String filename = "cdr-" + System.dateformat(dd - MM - yyyy) + System.currentTimeMillis() / 1000 + ".csv";
        try {
            while (rs.next()) {
                String line = rs.getString("cdr_id") + "," +
                        rs.getString("calling_number") + "," +
                        rs.getString("called_number") + "," +
                        rs.getString("call_date") + "," +
                        rs.getString("call_time") + "," +
                        rs.getString("duration") + "," +
                        rs.getString("cost") + "," +
                        rs.getString("revenue") + "," +
                        rs.getString("status") + "," +
                        rs.getString("created_at") + "," +
                        rs.getString("updated_at") + "," +
                        rs.getString("deleted_at");
                dbHandler.storeInFile(line, filename);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}