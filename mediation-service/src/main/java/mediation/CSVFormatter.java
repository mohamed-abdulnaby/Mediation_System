package mediation;

import db.DB;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.FileWriter;

public class CSVFormatter {
    public static void format() {

        String sql = "SELECT * FROM cdr";

        LocalDateTime date = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        String filename = "cdr-" + date.format(formatter) + System.currentTimeMillis() / 1000 + ".csv";
        try {
            List<Map<String, Object>> rs = DB.executeSelect(sql);
            for (Map<String, Object> s : rs) {
                String line = s.get("cdr_id") + "," +
                        s.get("calling_number") + "," +
                        s.get("called_number") + "," +
                        s.get("call_date") + "," +
                        s.get("call_time") + "," +
                        s.get("duration") + "," +
                        s.get("cost") + "," +
                        s.get("revenue") + "," +
                        s.get("status") + "," +
                        s.get("created_at") + "," +
                        s.get("updated_at") + "," +
                        s.get("deleted_at");
                storeInFile(line, filename);
            }
            FileSender.sendToAll(filename);
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