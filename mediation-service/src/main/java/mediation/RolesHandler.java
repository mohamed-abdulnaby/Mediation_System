package mediation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import db.DB;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/*
 * HTTP handler for /api/roles
 * Supports: GET (all), POST (create), PUT /{id} (update), DELETE /{id} (delete)
 */
public class RolesHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // CORS headers — needed when roles.html is opened as a local file
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");

        if ("OPTIONS".equals(ex.getRequestMethod())) { sendResponse(ex, 204, ""); return; }

        String path   = ex.getRequestURI().getPath(); // e.g. /api/roles or /api/roles/3
        String method = ex.getRequestMethod();
        String idStr  = path.replaceFirst("/api/roles/?", "").trim();

        try {
            if (method.equals("GET") && idStr.isEmpty()) {
                handleGetAll(ex);
            } else if (method.equals("POST") && idStr.isEmpty()) {
                handleCreate(ex);
            } else if (method.equals("PUT") && !idStr.isEmpty()) {
                handleUpdate(ex, Integer.parseInt(idStr));
            } else if (method.equals("DELETE") && !idStr.isEmpty()) {
                handleDelete(ex, Integer.parseInt(idStr));
            } else {
                sendResponse(ex, 404, "{\"error\":\"Not found\"}");
            }
        } catch (NumberFormatException e) {
            sendResponse(ex, 400, "{\"error\":\"Invalid ID\"}");
        } catch (Exception e) {
            System.err.println("[RolesHandler] Error: " + e.getMessage());
            sendResponse(ex, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void handleGetAll(HttpExchange ex) throws Exception {
        List<Map<String, Object>> rows = DB.executeSelect(
            "SELECT id, name, description, access_level, created_at FROM roles ORDER BY id"
        );
        sendResponse(ex, 200, toJsonArray(rows));
    }

    private void handleCreate(HttpExchange ex) throws Exception {
        Map<String, String> body = parseBody(ex);
        String name  = require(body, "name");
        String desc  = body.getOrDefault("description", "");
        String level = body.getOrDefault("access_level", "read-only");

        List<Map<String, Object>> result = DB.executeSelect(
            "INSERT INTO roles (name, description, access_level) VALUES (?, ?, ?) RETURNING id, name, description, access_level, created_at",
            name, desc, level
        );
        sendResponse(ex, 201, toJsonArray(result));
    }

    private void handleUpdate(HttpExchange ex, int id) throws Exception {
        Map<String, String> body = parseBody(ex);
        String name  = require(body, "name");
        String desc  = body.getOrDefault("description", "");
        String level = body.getOrDefault("access_level", "read-only");

        List<Map<String, Object>> result = DB.executeSelect(
            "UPDATE roles SET name=?, description=?, access_level=? WHERE id=? RETURNING id, name, description, access_level, created_at",
            name, desc, level, id
        );
        if (result.isEmpty()) { sendResponse(ex, 404, "{\"error\":\"Role not found\"}"); return; }
        sendResponse(ex, 200, toJsonArray(result));
    }

    private void handleDelete(HttpExchange ex, int id) throws Exception {
        int affected = DB.executeUpdate("DELETE FROM roles WHERE id=?", id);
        if (affected == 0) { sendResponse(ex, 404, "{\"error\":\"Role not found\"}"); return; }
        sendResponse(ex, 200, "{\"deleted\":true,\"id\":" + id + "}");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private Map<String, String> parseBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            String raw = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
            Map<String, String> map = new java.util.HashMap<>();
            // Simple JSON parser for flat objects: {"key":"value",...}
            raw = raw.replaceAll("^\\{|\\}$", "");
            for (String pair : raw.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                String[] kv = pair.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim().replaceAll("^\"|\"$", "");
                    String v = kv[1].trim().replaceAll("^\"|\"$", "");
                    map.put(k, v);
                }
            }
            return map;
        }
    }

    private String require(Map<String, String> body, String key) throws Exception {
        String v = body.get(key);
        if (v == null || v.isBlank()) throw new Exception("Missing required field: " + key);
        return v.trim();
    }

    private void sendResponse(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    // ── JSON serialisation (no external library) ──────────────────────────

    private String toJsonArray(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            sb.append(toJsonObject(rows.get(i)));
            if (i < rows.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonObject(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null)              sb.append("null");
            else if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else                        sb.append("\"").append(v.toString().replace("\\","\\\\").replace("\"","\\\"")).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }
}
