import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Minimal HTTP front end for the DBMS.
 *
 *   GET  /            web UI
 *   POST /api/query   body = one DBMS command, response = the command's console output
 *   POST /api/reset   restores the original dataset
 *   GET  /health      liveness check
 *
 * Uses only the JDK (com.sun.net.httpserver). Reads the port from the PORT env variable.
 */
public class WebServer {
    private static final int MAX_QUERY_CHARS = 2000;
    private static final Object LOCK = new Object();

    private static Mapping_task dbms;
    private static byte[] seedData;

    public static void main(String[] args) throws Exception {
        File dataFile = DbmsFiles.resolve(Mapping_task.FILE_NAME);
        seedData = Files.readAllBytes(dataFile.toPath());
        dbms = new Mapping_task();

        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            port = Integer.parseInt(portEnv.trim());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handle(exchange));
        server.setExecutor(null);
        server.start();
        System.out.println("NoSQL DBMS web server listening on port " + port);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                serveIndex(exchange);
            }
            else if ("GET".equals(method) && "/health".equals(path)) {
                send(exchange, 200, "text/plain; charset=utf-8", "ok");
            }
            else if ("POST".equals(method) && "/api/query".equals(path)) {
                String query = readBody(exchange);
                send(exchange, 200, "text/plain; charset=utf-8", runQuery(query));
            }
            else if ("POST".equals(method) && "/api/reset".equals(path)) {
                send(exchange, 200, "text/plain; charset=utf-8", resetData());
            }
            else {
                send(exchange, 404, "text/plain; charset=utf-8", "Not found");
            }
        }
        catch (Exception e) {
            send(exchange, 500, "text/plain; charset=utf-8", "Server error");
        }
        finally {
            exchange.close();
        }
    }

    private static String runQuery(String query) {
        query = query.trim();
        if (query.isEmpty()) {
            return "Enter a command.";
        }
        if (query.length() > MAX_QUERY_CHARS) {
            return "Command too long (max " + MAX_QUERY_CHARS + " characters).";
        }

        synchronized (LOCK) {
            PrintStream originalOut = System.out;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try {
                System.setOut(new PrintStream(buffer, true, "UTF-8"));
                dbms.processQuery(query);
            }
            catch (Exception e) {
                System.out.println("Error: could not process that command. Check the syntax.");
            }
            finally {
                System.out.flush();
                System.setOut(originalOut);
            }
            return cleanOutput(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    private static String resetData() throws IOException {
        synchronized (LOCK) {
            File dataFile = DbmsFiles.resolve(Mapping_task.FILE_NAME);
            Files.write(dataFile.toPath(), seedData);
            dbms = new Mapping_task();
        }
        return "Dataset reset to the original sample data.";
    }

    /** Hides server file paths and trims blank lines from console output. */
    private static String cleanOutput(String raw) {
        StringBuilder out = new StringBuilder();
        for (String line : raw.split("\\r?\\n")) {
            if (line.startsWith("Data successfully updated in")) {
                continue;
            }
            out.append(line).append('\n');
        }
        String result = out.toString().trim();
        return result.isEmpty() ? "(no output)" : result;
    }

    private static void serveIndex(HttpExchange exchange) throws IOException {
        InputStream in = WebServer.class.getResourceAsStream("/web/index.html");
        if (in == null) {
            send(exchange, 500, "text/plain; charset=utf-8", "UI not found");
            return;
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            send(exchange, 200, "text/html; charset=utf-8", new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        }
        finally {
            in.close();
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > MAX_QUERY_CHARS * 4) {
                break;
            }
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
