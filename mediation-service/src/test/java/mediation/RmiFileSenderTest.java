package mediation;

import org.telecom.common.RemoteFileService;
import org.telecom.common.AbstractRemoteFileService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RmiFileSenderTest {

    private static final int RMI_PORT = 1999;
    private static final String SERVICE_NAME = "TestFileService";
    private static final String TEST_DIR = "/tmp/rmi-test-input";

    public static void main(String[] args) throws Exception {
        System.out.println("=== RMI File Transfer Test ===\n");

        Path testDir = Path.of(TEST_DIR);
        Files.createDirectories(testDir);

        CountDownLatch received = new CountDownLatch(1);
        String[] receivedFilename = {null};
        byte[][] receivedData = {null};

        AbstractRemoteFileService testService = new AbstractRemoteFileService() {
            @Override
            protected void onFileReceived(String filename, byte[] data) {
                receivedFilename[0] = filename;
                receivedData[0] = data;
                received.countDown();
            }
        };

        UnicastRemoteObject.unexportObject(testService, true);
        RemoteFileService exported = (RemoteFileService) UnicastRemoteObject.exportObject(testService, 0);

        Registry registry = LocateRegistry.createRegistry(RMI_PORT);
        registry.rebind(SERVICE_NAME, (Remote) exported);
        System.out.println("[Test] RMI Service started on port " + RMI_PORT);

        Path testFile = testDir.resolve("test-cdr.csv");
        String csvContent = "id,calling,called,duration,cost,revenue,status\n" +
                "1,0101234567,0109876543,120,5.00,3.50,COMPLETED\n" +
                "2,0101111111,0109999999,60,2.50,1.75,PENDING\n";
        Files.writeString(testFile, csvContent);
        System.out.println("[Test] Created test file: " + testFile);

        RemoteFileService stub = (RemoteFileService) LocateRegistry.getRegistry("localhost", RMI_PORT).lookup(SERVICE_NAME);
        byte[] fileData = Files.readAllBytes(testFile);

        System.out.println("[Test] Sending file via RMI...");
        stub.receiveFile("test-cdr.csv", fileData);

        boolean success = received.await(5, TimeUnit.SECONDS);
        if (success) {
            System.out.println("[Test] File received! Filename: " + receivedFilename[0] + ", Size: " + receivedData[0].length + " bytes");
            Path savedFile = testDir.resolve(receivedFilename[0]);
            System.out.println("[Test] Content:\n" + new String(receivedData[0]));
            System.out.println("[Test] === PASSED ===");
        } else {
            System.out.println("[Test] === FAILED: File not received in time ===");
            success = false;
        }

        UnicastRemoteObject.unexportObject(testService, true);
        Files.walk(testDir).sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException e) {}
        });

        System.exit(success ? 0 : 1);
    }
}