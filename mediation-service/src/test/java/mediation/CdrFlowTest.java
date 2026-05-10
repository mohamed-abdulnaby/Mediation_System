package mediation;

import org.telecom.common.RemoteFileService;
import org.telecom.common.AbstractRemoteFileService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CdrFlowTest {

    private static final int RMI_PORT = 2999;
    private static final String BILLING_SERVICE = "BillingFileService";
    private static final String FRAUD_SERVICE = "FraudFileService";
    private static final String TEST_DIR = "/tmp/rmi-cdr-test";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("CDR Flow Integration Test (RMI)");
        System.out.println("=".repeat(60) + "\n");

        Path testDir = Path.of(TEST_DIR);
        Files.createDirectories(testDir);

        List<String> receivedFiles = new CopyOnWriteArrayList<>();
        List<byte[]> receivedData = new CopyOnWriteArrayList<>();
        CountDownLatch billingReady = new CountDownLatch(1);
        CountDownLatch fraudReady = new CountDownLatch(1);

        AbstractRemoteFileService billingService = new AbstractRemoteFileService() {
            @Override
            protected void onFileReceived(String filename, byte[] data) {
                receivedFiles.add("BILLING:" + filename);
                receivedData.add(data);
                System.out.println("[Billing] Received: " + filename + " (" + data.length + " bytes)");
                billingReady.countDown();
            }
        };

        AbstractRemoteFileService fraudService = new AbstractRemoteFileService() {
            @Override
            protected void onFileReceived(String filename, byte[] data) {
                receivedFiles.add("FRAUD:" + filename);
                System.out.println("[Fraud] Received: " + filename + " (" + data.length + " bytes)");
                fraudReady.countDown();
            }
        };

        Registry registry = LocateRegistry.createRegistry(RMI_PORT);

        UnicastRemoteObject.unexportObject(billingService, true);
        UnicastRemoteObject.unexportObject(fraudService, true);
        RemoteFileService billingExport = (RemoteFileService) UnicastRemoteObject.exportObject(billingService, 0);
        RemoteFileService fraudExport = (RemoteFileService) UnicastRemoteObject.exportObject(fraudService, 0);

        registry.rebind(BILLING_SERVICE, (java.rmi.Remote) billingExport);
        registry.rebind(FRAUD_SERVICE, (java.rmi.Remote) fraudExport);
        System.out.println("[Setup] RMI Services bound on port " + RMI_PORT + "\n");

        System.out.println("Step 1: Write test CSV file (simulating CDR data)");
        String csvFile = writeTestCsv();
        System.out.println("[Step 1] Created: " + csvFile + "\n");

        System.out.println("Step 2: Send CSV via RmiFileSender");
        RmiFileSender.sendToAll(csvFile, RMI_PORT);

        boolean billingOk = billingReady.await(5, TimeUnit.SECONDS);
        boolean fraudOk = fraudReady.await(5, TimeUnit.SECONDS);

        System.out.println();
        System.out.println("Step 3: Verify received data");
        System.out.println("-".repeat(40));

        boolean allPassed = true;

        if (billingOk) {
            long billingCount = receivedFiles.stream().filter(f -> f.startsWith("BILLING:")).count();
            System.out.println("[PASS] Billing received " + billingCount + " file(s)");

            for (byte[] data : receivedData) {
                String content = new String(data);
                System.out.println("\n[Billing] CSV Content (" + data.length + " bytes):");
                for (String line : content.split("\n")) {
                    System.out.println("  " + line);
                }

                String[] lines = content.split("\n");
                if (lines.length > 1) {
                    String header = lines[0];
                    if (header.contains("calling_number") && header.contains("called_number")) {
                        System.out.println("[PASS] CSV has valid CDR header with calling/called numbers");
                    } else {
                        System.out.println("[FAIL] CSV header missing expected CDR fields");
                        allPassed = false;
                    }
                } else {
                    System.out.println("[FAIL] CSV has no data rows");
                    allPassed = false;
                }
            }
        } else {
            System.out.println("[FAIL] Billing did not receive file");
            allPassed = false;
        }

        if (fraudOk) {
            long fraudCount = receivedFiles.stream().filter(f -> f.startsWith("FRAUD:")).count();
            System.out.println("[PASS] Fraud received " + fraudCount + " file(s)");
        } else {
            System.out.println("[FAIL] Fraud did not receive file");
            allPassed = false;
        }

        System.out.println();
        System.out.println("Step 4: Simulate CDR data (matching cdr_aggregated table)");
        System.out.println("-".repeat(40));
        System.out.println("CDR Aggregated Data (simulated):");
        System.out.println("  record_type  | window_type | window_start          | window_end            | total_records | total_duration | total_bytes | total_messages");
        System.out.println("  -------------+-------------+-----------------------+-----------------------+---------------+----------------+-------------+---------------");
        System.out.println("  MSC_VOICE    | HOURLY      | 2026-05-10 09:00:00   | 2026-05-10 10:00:00   | 1             | 330            | 0           | 0");
        System.out.println("  SMSC_SMS     | HOURLY      | 2026-05-10 10:00:00   | 2026-05-10 11:00:00   | 1             | 0              | 0           | 1");
        System.out.println("  PGW_DATA     | HOURLY      | 2026-05-10 11:00:00   | 2026-05-10 12:00:00   | 1             | 0              | 15728640    | 0");

        System.out.println();
        System.out.println("=".repeat(60));
        if (allPassed) {
            System.out.println("ALL TESTS PASSED");
        } else {
            System.out.println("SOME TESTS FAILED");
        }
        System.out.println("=".repeat(60));

        UnicastRemoteObject.unexportObject(billingService, true);
        UnicastRemoteObject.unexportObject(fraudService, true);

        Files.walk(testDir).sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException e) {}
        });

        System.exit(allPassed ? 0 : 1);
    }

    private static String writeTestCsv() throws IOException {
        Path csvPath = Path.of(TEST_DIR, "cdr-test-001.csv");
        StringBuilder sb = new StringBuilder();
        sb.append("id,calling_number,called_number,call_date,call_time,duration,cost,revenue,status,created_at,updated_at,deleted_at\n");
        sb.append("1,201001234567,201009876543,2026-05-10,09:00:00,330,15.50,10.25,COMPLETED,2026-05-10 09:05:30,2026-05-10 09:05:30,\n");
        sb.append("2,201005555555,201006666666,2026-05-10,10:00:00,5,0.50,0.30,DELIVERED,2026-05-10 10:00:05,2026-05-10 10:00:05,\n");
        sb.append("3,201007777777,2026551234,2026-05-10,11:00:00,1800,45.00,30.00,ACTIVE,2026-05-10 11:30:00,2026-05-10 11:30:00,\n");
        Files.writeString(csvPath, sb.toString());
        return csvPath.toString();
    }
}