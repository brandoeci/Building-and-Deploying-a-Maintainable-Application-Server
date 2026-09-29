package co.edu.escuelaing.webframework;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The entrance of the building: it accepts connections, reads the request,
 * asks the router who should answer, and writes the response.
 *
 * <p>It stays sequential: one connection is served completely and closed before
 * the next one is accepted. No threads, no executors, no queues.</p>
 *
 * <p>This class never knows what {@code /hello} does. Adding a service does not
 * change a single line here.</p>
 */
public class HttpServer {

    /** Port used when PORT is not set. */
    public static final int DEFAULT_PORT = 8080;

    /** A client that opens a socket and never finishes its request cannot block the loop forever. */
    private static final int CLIENT_READ_TIMEOUT_MS = 15_000;

    private final Router router = new Router();
    private final StaticFileService staticFiles = new StaticFileService();

    private volatile boolean running;
    private ServerSocket serverSocket;

    public Router router() {
        return router;
    }

    public StaticFileService staticFiles() {
        return staticFiles;
    }

    public boolean isRunning() {
        return running;
    }

    /** @return the port actually bound, useful when the server was started on port 0 */
    public int port() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    /**
     * Reads the port from the {@code PORT} environment variable.
     *
     * @return the configured port, or {@value #DEFAULT_PORT} when it is not set
     */
    public static int portFromEnvironment() {
        String value = System.getenv("PORT");
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("PORT is not a number: '" + value + "'. Using " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /**
     * Opens the listening socket.
     *
     * <p>No address is given on purpose: the server listens on every interface,
     * which is what the cloud platform needs. Binding to localhost only would
     * make it unreachable from outside the machine.</p>
     *
     * @return the port that was bound
     */
    public int bind(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        return port();
    }

    /** Binds if needed and serves connections until {@link #stop()} is called. */
    public void start(int port) throws IOException {
        if (serverSocket == null) {
            bind(port);
        }
        System.out.println("tdse-webframework listening on http://0.0.0.0:" + port());
        System.out.println("Static files: " + staticFiles.describeSource());
        System.out.println("Routes: " + router.size() + " registered, sequential mode");
        acceptLoop();
    }

    /**
     * The whole server: accept one connection, serve it, close it, and check
     * whether it should keep going.
     *
     * <p>That last check is what makes the shutdown graceful: {@code /shutdown}
     * only turns the flag off, so the current response is written and the
     * connection closed normally before the loop ends.</p>
     */
    public void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                serve(client);
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept failed: " + e.getMessage());
                }
            }
        }
        closeServerSocket();
        System.out.println("Server stopped gracefully.");
    }

    /** Asks the loop to end after the request being served right now. */
    public void stop() {
        running = false;
    }

    private void closeServerSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Could not close the listening socket: " + e.getMessage());
        } finally {
            serverSocket = null;
        }
    }

    /* ----------------------------------------------------------- one client */

    private void serve(Socket client) {
        long startedAt = System.currentTimeMillis();
        String summary = "-";
        try (Socket socket = client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            socket.setSoTimeout(CLIENT_READ_TIMEOUT_MS);

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return; // The browser opened a socket and closed it without asking anything.
            }

            Response response = new Response();
            try {
                Request request = Request.of(requestLine, readHeaders(in));
                summary = request.toString();
                handle(request, response);
            } catch (BadRequestException e) {
                error(response, 400, e.getMessage());
            }

            response.writeTo(out);
            System.out.printf("%s -> %d %s (%d bytes, %d ms)%n", summary, response.getStatus(),
                    response.getContentType(), response.getContentLength(),
                    System.currentTimeMillis() - startedAt);
        } catch (SocketTimeoutException e) {
            System.err.println("Client timed out before finishing its request");
        } catch (IOException e) {
            System.err.println("I/O failure while serving " + summary + ": " + e.getMessage());
        }
    }

    private Map<String, String> readHeaders(BufferedReader in) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
            }
        }
        return headers;
    }

    /**
     * Resolution order: a registered lambda first, then a static file, and a 404
     * when neither exists.
     */
    private void handle(Request request, Response response) {
        if (!"GET".equals(request.getMethod())) {
            error(response, 405, "Only GET is supported by this server");
            response.header("Allow", "GET");
            return;
        }

        Route route = router.find(request.getMethod(), request.getPath());
        if (route != null) {
            runHandler(route, request, response);
            return;
        }

        try {
            StaticFileService.StaticResource resource = staticFiles.find(request.getPath());
            if (resource != null) {
                response.status(200).type(resource.contentType()).body(resource.bytes());
                return;
            }
        } catch (StaticFileService.ForbiddenException e) {
            error(response, 403, e.getMessage());
            return;
        }

        error(response, 404, "404 Not Found");
    }

    /** A failing lambda becomes a 500; it never stops the server. */
    private void runHandler(Route route, Request request, Response response) {
        try {
            String body = route.handler().handle(request, response);
            // What the lambda returns is the body; if it returns null it already
            // wrote the response itself.
            if (body != null) {
                response.body(body);
            }
        } catch (Exception e) {
            System.err.println("Handler for " + route.path() + " failed: " + e);
            error(response, 500, "The service could not complete the request");
        }
    }

    private void error(Response response, int status, String message) {
        response.status(status).type(Response.TEXT_PLAIN).body(message);
    }
}
