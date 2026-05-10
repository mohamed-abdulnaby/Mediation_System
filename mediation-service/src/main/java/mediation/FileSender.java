package mediation;

import java.io.File;

public class FileSender {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_DELAY_MS = 1000;

    public static void sendToAll(String filename) {
        File file = new File(filename);

        if (!file.exists()) {
            System.err.println("File not found: " + file.getAbsolutePath());
            return;
        }

        System.out.println("Sending file: " + filename);

        sendToBilling(filename);
        sendToFraud(filename);
    }

    private static void sendToBilling(String filename) {
        String billingHost = System.getenv("BILLING_RMI_HOST");
        if (billingHost == null || billingHost.isEmpty()) {
            System.err.println("BILLING_RMI_HOST not configured");
            return;
        }

        System.out.println("Sending to billing: " + billingHost);
        sendWithRetry(filename, "Billing");
    }

    private static void sendToFraud(String filename) {
        String fraudHost = System.getenv("FRAUD_RMI_HOST");
        if (fraudHost == null || fraudHost.isEmpty()) {
            System.err.println("FRAUD_RMI_HOST not configured");
            return;
        }

        System.out.println("Sending to fraud: " + fraudHost);
        sendWithRetry(filename, "Fraud");
    }

    private static void sendWithRetry(String filename, String serviceName) {
        int attempt = 0;
        long delay = INITIAL_DELAY_MS;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                RmiFileSender.sendToAll(filename);
                System.out.println(serviceName + " upload succeeded (attempt " + attempt + ")");
                return;
            } catch (Exception e) {
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