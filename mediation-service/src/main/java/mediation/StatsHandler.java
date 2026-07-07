package mediation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import db.DB;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/*
 * HTTP handler for /api/stats
 * Returns a JSON object containing live system statistics.
 */
public class StatsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // CORS headers
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            sendResponse(ex, 204, "");
            return;
        }

        try {
            long totalCdr = getCount("SELECT COUNT(*) FROM mediation_cdr");
            long voiceCdr = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE record_type = 'MscVoiceCdr'");
            long smsCdr   = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE record_type = 'SmscCdr'");
            long dataCdr  = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE record_type = 'PgwDataCdr'");
            long voiceSec = getSum("SELECT SUM(duration) FROM mediation_cdr");
            long aggCount = getCount("SELECT COUNT(*) FROM mediation_cdr_aggregated");
            long dataBytes = getSum("SELECT SUM(total_bytes) FROM mediation_cdr_aggregated");
            long smsMsgs  = getSum("SELECT SUM(total_messages) FROM mediation_cdr_aggregated");

            String json = String.format(
                "{" +
                "\"total_cdr\":%d," +
                "\"voice_cdr\":%d," +
                "\"sms_cdr\":%d," +
                "\"data_cdr\":%d," +
                "\"total_duration_sec\":%d," +
                "\"total_bytes\":%d," +
                "\"total_messages\":%d," +
                "\"total_aggregated_buckets\":%d" +
                "}",
                totalCdr, voiceCdr, smsCdr, dataCdr, voiceSec, dataBytes, smsMsgs, aggCount
            );

            sendResponse(ex, 200, json);
        } catch (Exception e) {
            System.err.println("[StatsHandler] Error: " + e.getMessage());
            sendResponse(ex, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private long getCount(String sql) {
        try {
            List<Map<String, Object>> res = DB.executeSelect(sql);
            if (!res.isEmpty()) {
                Object val = res.get(0).values().iterator().next();
                if (val instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (Exception e) {
            System.err.println("[StatsHandler] getCount failed: " + e.getMessage());
        }
        return 0;
    }

    private long getSum(String sql) {
        try {
            List<Map<String, Object>> res = DB.executeSelect(sql);
            if (!res.isEmpty()) {
                Object val = res.get(0).values().iterator().next();
                if (val instanceof Number n) {
                    return n.longValue();
                }
            }
        } catch (Exception e) {
            // Null returns on empty tables are expected
        }
        return 0;
    }

    private void sendResponse(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
