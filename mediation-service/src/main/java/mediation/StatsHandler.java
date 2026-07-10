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
            long totalCdr = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE rejection_reason IS NULL");
            long voiceCdr = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE record_type = 'MscVoiceCdr' AND rejection_reason IS NULL");
            long smsCdr   = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE record_type = 'SmscCdr' AND rejection_reason IS NULL");
            long dataCdr  = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE record_type = 'PgwDataCdr' AND rejection_reason IS NULL");
            long voiceSec = getSum("SELECT SUM(duration) FROM mediation_cdr WHERE record_type = 'MscVoiceCdr' AND rejection_reason IS NULL");
            long aggCount = getCount("SELECT COUNT(*) FROM mediation_cdr_aggregated");
            long dataBytes = getSum("SELECT SUM(total_bytes) FROM mediation_cdr_aggregated");
            long smsMsgs  = getSum("SELECT SUM(total_messages) FROM mediation_cdr_aggregated");
            long rejectedCdr = getCount("SELECT COUNT(*) FROM mediation_cdr WHERE rejection_reason IS NOT NULL");

            // Query recent rejected logs
            List<Map<String, Object>> rejectedLogs = DB.executeSelect(
                "SELECT dial_a, record_type, source_file, rejection_reason, created_at " +
                "FROM mediation_cdr " +
                "WHERE rejection_reason IS NOT NULL " +
                "ORDER BY id DESC LIMIT 5"
            );

            StringBuilder logsJson = new StringBuilder("[");
            for (int i = 0; i < rejectedLogs.size(); i++) {
                Map<String, Object> log = rejectedLogs.get(i);
                String dialA = String.valueOf(log.getOrDefault("dial_a", ""));
                String recordType = String.valueOf(log.getOrDefault("record_type", ""));
                String sourceFile = String.valueOf(log.getOrDefault("source_file", ""));
                String reason = String.valueOf(log.getOrDefault("rejection_reason", ""));
                String createdAt = String.valueOf(log.getOrDefault("created_at", ""));

                logsJson.append(String.format(
                    "{\"dial_a\":\"%s\",\"record_type\":\"%s\",\"source_file\":\"%s\",\"rejection_reason\":\"%s\",\"created_at\":\"%s\"}",
                    escapeJson(dialA), escapeJson(recordType), escapeJson(sourceFile), escapeJson(reason), escapeJson(createdAt)
                ));
                if (i < rejectedLogs.size() - 1) {
                    logsJson.append(",");
                }
            }
            logsJson.append("]");

            String json = String.format(
                "{" +
                "\"total_cdr\":%d," +
                "\"voice_cdr\":%d," +
                "\"sms_cdr\":%d," +
                "\"data_cdr\":%d," +
                "\"total_duration_sec\":%d," +
                "\"total_bytes\":%d," +
                "\"total_messages\":%d," +
                "\"total_aggregated_buckets\":%d," +
                "\"rejected_cdr\":%d," +
                "\"rejected_logs\":%s" +
                "}",
                totalCdr, voiceCdr, smsCdr, dataCdr, voiceSec, dataBytes, smsMsgs, aggCount, rejectedCdr, logsJson.toString()
            );

            sendResponse(ex, 200, json);
        } catch (Exception e) {
            System.err.println("[StatsHandler] Error: " + e.getMessage());
            sendResponse(ex, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
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
