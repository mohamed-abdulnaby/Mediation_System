package mediation;

import org.telecom.common.HttpClientUtil;
import db.DB;

import java.io.File;
import java.io.IOException;

public class FileSender {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;

    public static void sendToAll(String filename) {
        String billingUrl = System.getenv("BILLING_URL");
        String fraudUrl = System.getenv("FRAUD_URL");

        File file = new File(filename);

        if (!file.exists()) {
            System.err.println("File not found: " + file.getAbsolutePath());
            return;
        }

        System.out.println("Sending file: " + filename);

        sendToBilling(file, billingUrl);
        sendToFraud(file, fraudUrl);
    }

    private static void sendToBilling(File file, String url) {
        if (url == null || url.isEmpty()) {
            System.err.println("BILLING_URL not configured");
            return;
        }

        System.out.println("Sending to billing: " + url);
        sendWithRetry(url, file, "Billing");
    }

    private static void sendToFraud(File file, String url) {
        if (url == null || url.isEmpty()) {
            System.err.println("FRAUD_URL not configured");
            return;
        }

        System.out.println("Sending to fraud: " + url);
        sendWithRetry(url, file, "Fraud");
    }

    private static void sendWithRetry(String url, File file, String serviceName) {
        int attempt = 0;
        long delay = INITIAL_DELAY_MS;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                HttpClientUtil.sendFile(url, file);
                System.out.println(serviceName + " upload succeeded (attempt " + attempt + ")");
                return;
            } catch (IOException e) {
                System.err.println(serviceName + " upload failed (attempt " + attempt + "): " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    System.out.println("Retrying in " + delay / 1000 + "s...");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delay *= 2;
                }
            }
        }
        System.err.println(serviceName + " failed after " + MAX_RETRIES + " attempts");
    }

}