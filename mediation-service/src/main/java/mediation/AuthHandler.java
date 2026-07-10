package mediation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import db.DB;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuthHandler implements HttpHandler {

    static {
        // Initialize the users table at startup if it does not exist
        try (Connection conn = DB.getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Create users table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password_hash VARCHAR(100) NOT NULL,
                    role_id INT REFERENCES roles(id) ON DELETE SET NULL,
                    created_at TIMESTAMPTZ DEFAULT NOW()
                )
            """);

            // 2. Ensure roles table has Administrator, Operator, and Viewer roles
            if (getCount(stmt, "SELECT COUNT(*) FROM roles WHERE access_level = 'admin'") == 0) {
                stmt.execute("INSERT INTO roles (name, description, access_level) VALUES ('Administrator', 'Full access', 'admin')");
            }
            if (getCount(stmt, "SELECT COUNT(*) FROM roles WHERE access_level = 'manager'") == 0) {
                stmt.execute("INSERT INTO roles (name, description, access_level) VALUES ('Operator', 'Modify permissions', 'manager')");
            }
            if (getCount(stmt, "SELECT COUNT(*) FROM roles WHERE access_level = 'read-only'") == 0) {
                stmt.execute("INSERT INTO roles (name, description, access_level) VALUES ('Viewer', 'Read-only access', 'read-only')");
            }

            // Seed default users linked to their roles in mediation_cdr DB (we fetch role IDs first)
            List<Map<String, Object>> roles = DB.executeSelect("SELECT id, access_level FROM roles");
            int adminRoleId = 1;
            int managerRoleId = 2;
            int viewerRoleId = 3;
            for (Map<String, Object> r : roles) {
                String level = String.valueOf(r.get("access_level"));
                int id = ((Number) r.get("id")).intValue();
                if ("admin".equals(level)) adminRoleId = id;
                else if ("manager".equals(level)) managerRoleId = id;
                else if ("read-only".equals(level)) viewerRoleId = id;
            }

            // Seed default accounts (SHA-256 for passwords 'admin', 'manager', 'viewer')
            if (getCount(stmt, "SELECT COUNT(*) FROM users WHERE username = 'admin'") == 0) {
                stmt.execute("INSERT INTO users (username, password_hash, role_id) VALUES ('admin', '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', " + adminRoleId + ")");
            } else {
                stmt.execute("UPDATE users SET password_hash = '8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918', role_id = " + adminRoleId + " WHERE username = 'admin'");
            }
            if (getCount(stmt, "SELECT COUNT(*) FROM users WHERE username = 'manager'") == 0) {
                stmt.execute("INSERT INTO users (username, password_hash, role_id) VALUES ('manager', '6ee4a469cd4e91053847f5d3fcb61dbcc91e8f0ef10be7748da4c4a1ba382d17', " + managerRoleId + ")");
            } else {
                stmt.execute("UPDATE users SET password_hash = '6ee4a469cd4e91053847f5d3fcb61dbcc91e8f0ef10be7748da4c4a1ba382d17', role_id = " + managerRoleId + " WHERE username = 'manager'");
            }
            if (getCount(stmt, "SELECT COUNT(*) FROM users WHERE username = 'viewer'") == 0) {
                stmt.execute("INSERT INTO users (username, password_hash, role_id) VALUES ('viewer', 'd35ca5051b82ffc326a3b0b6574a9a3161dee16b9478a199ee39cd803ce5b799', " + viewerRoleId + ")");
            } else {
                stmt.execute("UPDATE users SET password_hash = 'd35ca5051b82ffc326a3b0b6574a9a3161dee16b9478a199ee39cd803ce5b799', role_id = " + viewerRoleId + " WHERE username = 'viewer'");
            }

            System.out.println("[AuthHandler] Successfully checked and initialized users/roles tables in database.");
        } catch (Exception e) {
            System.err.println("[AuthHandler] Warning: failed to auto-initialize users table: " + e.getMessage());
        }
    }

    private static long getCount(Statement stmt, String sql) {
        try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) return rs.getLong(1);
        } catch (Exception e) {
            // ignore
        }
        return 0;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // CORS Headers
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        String path = ex.getRequestURI().getPath();
        if ("/api/login".equals(path)) {
            handleLogin(ex);
        } else if ("/api/logout".equals(path)) {
            handleLogout(ex);
        } else if ("/api/me".equals(path)) {
            handleMe(ex);
        } else {
            ex.sendResponseHeaders(404, -1);
        }
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8));
            StringBuilder bodyBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                bodyBuilder.append(line);
            }
            String body = bodyBuilder.toString();

            String username = extractJsonVal(body, "username");
            String password = extractJsonVal(body, "password");

            if (username == null || password == null) {
                sendJson(ex, 400, "{\"error\":\"Missing username or password\"}");
                return;
            }

            String hash = sha256(password);

            List<Map<String, Object>> users = DB.executeSelect(
                "SELECT u.username, r.access_level FROM users u " +
                "LEFT JOIN roles r ON u.role_id = r.id " +
                "WHERE u.username = ? AND u.password_hash = ?",
                username, hash
            );

            if (users.isEmpty()) {
                sendJson(ex, 401, "{\"error\":\"Invalid username or password\"}");
                return;
            }

            Map<String, Object> user = users.get(0);
            String level = String.valueOf(user.getOrDefault("access_level", "read-only"));

            String token = UUID.randomUUID().toString();
            AuthFilter.activeSessions.put(token, new AuthFilter.UserSession(username, level));

            ex.getResponseHeaders().add("Set-Cookie", "SESSION_ID=" + token + "; Path=/; HttpOnly; SameSite=Lax");
            sendJson(ex, 200, String.format("{\"success\":true,\"username\":\"%s\",\"access_level\":\"%s\"}", username, level));

        } catch (Exception e) {
            sendJson(ex, 500, "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        String sessionId = getSessionIdFromCookie(ex);
        if (sessionId != null) {
            AuthFilter.activeSessions.remove(sessionId);
        }
        ex.getResponseHeaders().add("Set-Cookie", "SESSION_ID=; Path=/; HttpOnly; Max-Age=0");
        sendJson(ex, 200, "{\"success\":true}");
    }

    private void handleMe(HttpExchange ex) throws IOException {
        String sessionId = getSessionIdFromCookie(ex);
        AuthFilter.UserSession session = (sessionId != null) ? AuthFilter.activeSessions.get(sessionId) : null;
        if (session == null) {
            sendJson(ex, 401, "{\"error\":\"Not logged in\"}");
        } else {
            sendJson(ex, 200, String.format("{\"username\":\"%s\",\"access_level\":\"%s\"}", session.username, session.accessLevel));
        }
    }

    private String getSessionIdFromCookie(HttpExchange ex) {
        List<String> cookies = ex.getRequestHeaders().get("Cookie");
        if (cookies != null) {
            for (String cookieHeader : cookies) {
                String[] parts = cookieHeader.split(";");
                for (String part : parts) {
                    String[] pair = part.trim().split("=");
                    if (pair.length == 2 && "SESSION_ID".equalsIgnoreCase(pair[0])) {
                        return pair[1];
                    }
                }
            }
        }
        return null;
    }

    private String extractJsonVal(String body, String key) {
        int idx = body.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int colonIdx = body.indexOf(":", idx);
        if (colonIdx == -1) return null;
        int quoteStart = body.indexOf("\"", colonIdx);
        if (quoteStart == -1) return null;
        int quoteEnd = body.indexOf("\"", quoteStart + 1);
        if (quoteEnd == -1) return null;
        return body.substring(quoteStart + 1, quoteEnd);
    }

    private String sha256(String base) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(base.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
