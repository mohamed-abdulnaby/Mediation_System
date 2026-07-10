package mediation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthFilter implements HttpHandler {
    private final HttpHandler next;

    public static class UserSession {
        public final String username;
        public final String accessLevel; // "admin", "manager", "read-only"
        public final long createdAt;
        public UserSession(String username, String accessLevel) {
            this.username = username;
            this.accessLevel = accessLevel;
            this.createdAt = System.currentTimeMillis();
        }
    }
    
    public static final Map<String, UserSession> activeSessions = new ConcurrentHashMap<>();

    public AuthFilter(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        // 1. Cookie resolution
        String sessionId = getSessionIdFromCookie(ex);
        UserSession session = (sessionId != null) ? activeSessions.get(sessionId) : null;

        // 2. Static page exceptions / Redirect bypass
        if (path.equals("/login.html")) {
            if (session != null) {
                ex.getResponseHeaders().add("Location", "/");
                ex.sendResponseHeaders(302, -1);
                return;
            }
            next.handle(ex);
            return;
        }

        if (session == null) {
            if (path.startsWith("/api/")) {
                ex.getResponseHeaders().add("Content-Type", "application/json");
                byte[] response = "{\"error\":\"Unauthorized. Please log in.\"}".getBytes();
                ex.sendResponseHeaders(401, response.length);
                ex.getResponseBody().write(response);
                ex.getResponseBody().close();
                return;
            }
            ex.getResponseHeaders().add("Location", "/login.html");
            ex.sendResponseHeaders(302, -1);
            return;
        }

        // 3. RBAC Enforcement for modifications
        if (path.startsWith("/api/roles")) {
            String method = ex.getRequestMethod();
            if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                String level = session.accessLevel;
                if (!"admin".equalsIgnoreCase(level) && !"manager".equalsIgnoreCase(level)) {
                    sendForbidden(ex);
                    return;
                }
                if ("DELETE".equals(method) && !"admin".equalsIgnoreCase(level)) {
                    sendForbidden(ex);
                    return;
                }
            }
        }

        next.handle(ex);
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

    private void sendForbidden(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Content-Type", "application/json");
        byte[] response = "{\"error\":\"Forbidden. Insufficient permissions.\"}".getBytes();
        ex.sendResponseHeaders(403, response.length);
        ex.getResponseBody().write(response);
        ex.getResponseBody().close();
    }
}
