import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@WebServlet(name = "CrptApi", urlPatterns = "/document")
public class CrptApi extends HttpServlet {

    private final Semaphore rateLimiter = new Semaphore(5);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        String json = sb.toString();
        Document document = objectMapper.readValue(json, Document.class);

        createDocument(document, "your_signature");

        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        out.print("{\"status\":\"received\"}");
        out.flush();
    }

    public void createDocument(Document document, String signature) {
        try {
            if (!rateLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
                System.out.println("Превышен лимит запросов, ожидание...");
                rateLimiter.acquire();
            }

            String requestBody = objectMapper.writeValueAsString(document);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Ответ сервера: " + response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Произошла ошибка при ожидании разрешения семафора: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Произошла ошибка ввода/вывода при работе с HTTP-клиентом: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Произошла ошибка: " + e.getMessage());
        } finally {
            rateLimiter.release();
        }
    }

    @Data
    public static class Document {
        private String description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;
    }

    @Data
    public static class Product {
        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }
}
