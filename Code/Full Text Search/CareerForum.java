import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CareerForumDemoServer {
    private static final int PORT = Integer.getInteger("port", 8080);

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        server.createContext("/", CareerForumDemoServer::servePage);
        server.start();
        System.out.println("Career Forum demo is running at http://localhost:" + PORT);
        System.out.println("Keep this terminal open. Press Ctrl+C to stop the server.");
    }

    private static void servePage(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
            return;
        }

        byte[] page = loadPage();
        if (page == null) {
            String help = "Could not find index.html. Open the extracted project folder in VS Code and run from the project root. Expected: src/main/resources/static/index.html";
            send(exchange, 500, "text/plain; charset=UTF-8", help);
            return;
        }
        send(exchange, 200, "text/html; charset=UTF-8", page);
    }

    private static byte[] loadPage() throws IOException {
        try (var in = CareerForumDemoServer.class.getResourceAsStream("/static/index.html")) {
            if (in != null) return in.readAllBytes();
        }
        Path current = Paths.get(System.getProperty("user.dir"));
        Path[] candidates = {
            current.resolve("src/main/resources/static/index.html"),
            current.resolve("career_forum_demo/src/main/resources/static/index.html"),
            current.resolve("../career_forum_demo/src/main/resources/static/index.html")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return Files.readAllBytes(candidate);
        }
        return null;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        send(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
