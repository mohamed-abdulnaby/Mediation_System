package mediation;

import org.telecom.common.RemoteFileService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class RmiFileSender {

    private static final int RMI_PORT = 1099;
    private static final String BILLING_SERVICE_NAME = "BillingFileService";
    private static final String FRAUD_SERVICE_NAME = "FraudFileService";

    public static void sendToAll(String filename) {
        String billingHost = resolveHost("BILLING_RMI_HOST", "billing");
        String fraudHost = resolveHost("FRAUD_RMI_HOST", "fraud");

        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("[RmiFileSender] File not found: " + file.getAbsolutePath());
            return;
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            System.err.println("[RmiFileSender] Failed to read file: " + e.getMessage());
            return;
        }

        System.out.println("[RmiFileSender] Sending: " + filename + " (" + data.length + " bytes)");

        sendTo(BILLING_SERVICE_NAME, billingHost, filename, data);
        sendTo(FRAUD_SERVICE_NAME, fraudHost, filename, data);
    }

    public static void sendToAll(String filename, int port) {
        String billingHost = resolveHost("BILLING_RMI_HOST", "localhost");
        String fraudHost = resolveHost("FRAUD_RMI_HOST", "localhost");

        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("[RmiFileSender] File not found: " + file.getAbsolutePath());
            return;
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            System.err.println("[RmiFileSender] Failed to read file: " + e.getMessage());
            return;
        }

        System.out.println("[RmiFileSender] Sending: " + filename + " (" + data.length + " bytes)");

        sendTo(BILLING_SERVICE_NAME, billingHost, filename, data, port);
        sendTo(FRAUD_SERVICE_NAME, fraudHost, filename, data, port);
    }

    private static void sendTo(String serviceName, String host, String filename, byte[] data) {
        sendTo(serviceName, host, filename, data, RMI_PORT);
    }

    private static void sendTo(String serviceName, String host, String filename, byte[] data, int port) {
        try {
            Registry registry = LocateRegistry.getRegistry(host, port);
            RemoteFileService stub = (RemoteFileService) registry.lookup(serviceName);
            stub.receiveFile(filename, data);
            System.out.println("[RmiFileSender] Sent to " + serviceName + " (" + host + "): " + filename);
        } catch (Exception e) {
            System.err.println("[RmiFileSender] Failed to send to " + serviceName + ": " + e.getMessage());
        }
    }

    private static String resolveHost(String envKey, String fallback) {
        String host = System.getenv(envKey);
        return (host != null && !host.isEmpty()) ? host : fallback;
    }
}