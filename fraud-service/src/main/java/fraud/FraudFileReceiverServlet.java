package fraud;

import org.telecom.common.FileReceiverServlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/upload")
public class FraudFileReceiverServlet extends FileReceiverServlet {
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        System.out.println("=== FILE RECEIVED at Fraud Service ===");
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            System.out.println(name + " = " + request.getHeader(name));
        });
        response.getWriter().println("Fraud Service received your file.");
    }
}