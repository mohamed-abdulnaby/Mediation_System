package billing;

import org.telecom.common.AbstractRemoteFileService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

public class BillingRmiService extends AbstractRemoteFileService {

    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "BillingFileService";
    private static final String INPUT_DIR = "/app/input";

    public BillingRmiService() throws Exception {
        super();
        Files.createDirectories(Path.of(INPUT_DIR));
    }

    @Override
    protected void onFileReceived(String filename, byte[] data) {
        try {
            Path filePath = Path.of(INPUT_DIR, filename);
            Files.write(filePath, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("[BillingRmiService] Saved: " + filename + " (" + data.length + " bytes)");
        } catch (Exception e) {
            System.err.println("[BillingRmiService] Failed to save: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.rmi.server.hostname", "billing");
        var service = new BillingRmiService();
        LocateRegistry.createRegistry(RMI_PORT).rebind(SERVICE_NAME, service);
        System.out.println("[BillingRmiService] Running on port " + RMI_PORT);
    }
}