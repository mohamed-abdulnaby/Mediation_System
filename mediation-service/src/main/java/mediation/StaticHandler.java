package mediation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/*
 * Serves roles.html from the classpath (src/main/resources/static/).
 * Handles GET / and GET /roles → returns roles.html
 */
public class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }

        InputStream resource = getClass().getResourceAsStream("/static/roles.html");
        if (resource == null) {
            String err = "roles.html not found in classpath. Ensure it is in src/main/resources/static/";
            ex.sendResponseHeaders(404, err.length());
            try (OutputStream os = ex.getResponseBody()) { os.write(err.getBytes()); }
            return;
        }

        byte[] bytes = resource.readAllBytes();
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
