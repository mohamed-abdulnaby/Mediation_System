package mediation;

import java.io.File;

public class FileSender {

    private static final int  MAX_RETRIES      = 3;
    private static final long INITIAL_DELAY_MS = 1000;

    public static void sendToAll(String filename) {
        File file = new File(filename);

        if (!file.exists()) {
            System.err.println("[FileSender] File not found: " + file.getAbsolutePath());
            return;
        }

        // Guard: both env vars must be set before attempting to send
        String billingHost = System.getenv("BILLING_RMI_HOST");
        String fraudHost   = System.getenv("FRAUD_RMI_HOST");

        if (billingHost == null || billingHost.isEmpty()) {
            System.err.println("[FileSender] BILLING_RMI_HOST not configured — skipping send");
            return;
        }
        if (fraudHost == null || fraudHost.isEmpty()) {
            System.err.println("[FileSender] FRAUD_RMI_HOST not configured — skipping send");
            return;
        }

        System.out.println("[FileSender] Sending: " + filename);

        // Single retry loop — RmiFileSender.sendToAll() handles both services internally.
        // Previously sendToBilling() and sendToFraud() each called sendWithRetry() which
        // called sendToAll(), causing every file to be sent twice to each service.
        int  attempt = 0;
        long delay   = INITIAL_DELAY_MS;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                RmiFileSender.sendToAll(filename);
                System.out.println("[FileSender] Sent successfully (attempt " + attempt + ")");
                return;
            } catch (Exception e) {
                System.err.println("[FileSender] Send failed (attempt " + attempt + "): " + e.getMessage());
                if (attempt < MAX_RETRIES) {
                    System.out.println("[FileSender] Retrying in " + delay / 1000 + "s...");
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    delay *= 2; // exponential backoff: 1s → 2s → 4s
                }
            }
        }
        System.err.println("[FileSender] Failed after " + MAX_RETRIES + " attempts — file: " + filename);
    }
}