package mediation;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) {
        // ── 1. Start the Admin HTTP server on port 8080 ──────────────────
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            
            // Authentication routes (unfiltered)
            AuthHandler authHandler = new AuthHandler();
            server.createContext("/api/login",  authHandler);
            server.createContext("/api/logout", authHandler);
            server.createContext("/api/me",     authHandler);

            // Secure administrative routes (filtered)
            server.createContext("/api/roles", new AuthFilter(new RolesHandler()));
            server.createContext("/api/stats", new AuthFilter(new StatsHandler()));
            server.createContext("/",          new AuthFilter(new StaticHandler()));

            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
            System.out.println("Admin UI running at http://localhost:8080");
            System.out.println("Roles API at       http://localhost:8080/api/roles");
        } catch (Exception e) {
            System.err.println("Failed to start HTTP server: " + e.getMessage());
        }

        // ── 2. Start the FTP polling loop (blocks forever) ───────────────
        System.out.println("Starting mediation FTP poller...");
        FtpDownloader downloader = new FtpDownloader();
        downloader.pollFromFtp();
    }
}