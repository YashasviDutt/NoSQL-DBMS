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
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Minimal HTTP front end for the DBMS. Every visitor gets a private copy of the dataset,
 * tied to a "sid" cookie, so visitors cannot affect each other.
 *
 *   GET  /            web UI
 *   POST /api/query   body = one DBMS command, response = the command's console output
 *   POST /api/reset   restores the visitor's dataset to the original sample data
 *   GET  /health      liveness check
 *
 * Uses only the JDK (com.sun.net.httpserver). Reads the port from the PORT env variable.
 */
public class WebServer {
    private static final int MAX_QUERY_CHARS = 2000;
    private static final int MAX_SESSIONS = 200;
    private static final long SESSION_TTL_MS = 30L * 60 * 1000;
    private static final String COOKIE_NAME = "sid";

    /** Guards the session table and System.out redirection (which is process-wide). */
    private static final Object LOCK = new Object();

    private static final SecureRandom RANDOM = new SecureRandom();
    // Access-ordered so the least recently used session is first.
    private static final Map<String, Session> SESSIONS = new LinkedHashMap<String, Session>(16, 0.75f, true);

    private static byte[] seedData;
    private static File sessionDir;

    private static class Session {
        final String id;
        final File file;
        Mapping_task db;
        long lastAccess;

        Session(String id, File file, Mapping_task db) {
            this.id = id;
            this.file = file;
            this.db = db;
            this.lastAccess = System.currentTimeMillis();
        }
    }

    public static void main(String[] args) throws Exception {
        seedData = Files.readAllBytes(DbmsFiles.resolve(Mapping_task.FILE_NAME).toPath());
        sessionDir = Files.createTempDirectory("dbms-sessions").toFile();

        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.trim().isEmpty()) {
            port = Integer.parseInt(portEnv.trim());
        }

        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "session-cleaner");
                t.setDaemon(true);
                return t;
            }
        });
        cleaner.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                expireSessions();
            }
        }, 5, 5, TimeUnit.MINUTES);

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
                String result = withSession(exchange, query, false);
                send(exchange, 200, "text/plain; charset=utf-8", result);
            }
            else if ("POST".equals(method) && "/api/reset".equals(path)) {
                send(exchange, 200, "text/plain; charset=utf-8", withSession(exchange, null, true));
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

    /** Finds or creates the caller's session, then either resets it or runs a query on it. */
    private static String withSession(HttpExchange exchange, String query, boolean reset) throws IOException {
        synchronized (LOCK) {
            Session session = findSession(readSessionId(exchange));
            if (session == null) {
                session = createSession();
            }
            session.lastAccess = System.currentTimeMillis();
            setSessionCookie(exchange, session.id);

            if (reset) {
                Files.write(session.file.toPath(), seedData);
                session.db = new Mapping_task(session.file);
                return "Your dataset was reset to the original sample data.";
            }
            return runQuery(session, query);
        }
    }

    private static String runQuery(Session session, String query) {
        query = query.trim();
        if (query.isEmpty()) {
            return "Enter a command.";
        }
        if (query.length() > MAX_QUERY_CHARS) {
            return "Command too long (max " + MAX_QUERY_CHARS + " characters).";
        }

        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buffer, true, "UTF-8"));
            session.db.processQuery(query);
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

    // ---- sessions (callers must hold LOCK) ----

    private static Session findSession(String id) {
        return id == null ? null : SESSIONS.get(id);
    }

    private static Session createSession() throws IOException {
        while (SESSIONS.size() >= MAX_SESSIONS) {
            Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet().iterator();
            Session oldest = it.next().getValue();
            it.remove();
            deleteQuietly(oldest.file);
        }

        String id = newSessionId();
        File file = new File(sessionDir, id + ".data");
        Files.write(file.toPath(), seedData);
        Session session = new Session(id, file, new Mapping_task(file));
        SESSIONS.put(id, session);
        return session;
    }

    private static void expireSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MS;
        synchronized (LOCK) {
            Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet().iterator();
            while (it.hasNext()) {
                Session s = it.next().getValue();
                if (s.lastAccess < cutoff) {
                    it.remove();
                    deleteQuietly(s.file);
                }
            }
        }
    }

    private static void deleteQuietly(File file) {
        if (file != null && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private static String newSessionId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String readSessionId(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            String p = part.trim();
            if (p.startsWith(COOKIE_NAME + "=")) {
                String value = p.substring(COOKIE_NAME.length() + 1);
                return value.matches("[0-9a-f]{32}") ? value : null;
            }
        }
        return null;
    }

    private static void setSessionCookie(HttpExchange exchange, String id) {
        String cookie = COOKIE_NAME + "=" + id + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400";
        if ("https".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-Forwarded-Proto"))) {
            cookie += "; Secure";
        }
        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    // ---- helpers ----

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
