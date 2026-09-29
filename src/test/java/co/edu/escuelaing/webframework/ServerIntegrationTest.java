package co.edu.escuelaing.webframework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Talks to the server over real sockets, with the same routes the example
 * application registers.
 *
 * <p>The accept loop runs in a thread so the test can be the client. That thread
 * belongs to the test: the server still serves one connection at a time.</p>
 */
class ServerIntegrationTest {

    private HttpServer server;
    private Thread serverThread;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = new HttpServer();
        server.staticFiles().setRoot("/webroot");

        server.router().add("GET", "/hello", (req, resp) -> {
            String name = req.getValue("name");
            return "Hello " + (name == null || name.isBlank() ? "world" : name);
        });
        server.router().add("GET", "/pi", (req, resp) -> String.valueOf(Math.PI));
        server.router().add("GET", "/json", (req, resp) -> {
            resp.type(Response.APPLICATION_JSON);
            return "{\"ok\":true}";
        });
        server.router().add("GET", "/boom", (req, resp) -> {
            throw new IllegalStateException("the handler failed on purpose");
        });
        server.router().add("GET", "/shutdown", (req, resp) -> {
            server.stop();
            return "Server will stop after this response.";
        });

        int port = server.bind(0);
        baseUrl = "http://localhost:" + port;
        serverThread = new Thread(server::acceptLoop, "test-accept-loop");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        server.stop();
        // Wake the accept() call up so the loop can notice the flag.
        try (Socket ignored = new Socket("localhost", URI.create(baseUrl).getPort())) {
            // nothing to send
        } catch (IOException e) {
            // already closed
        }
        serverThread.join(2000);
    }

    /* ------------------------------------------------------------- helpers */

    private record Answer(int status, String contentType, byte[] body, HttpURLConnection connection) {
        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private Answer request(String method, String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(baseUrl + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(20000);
        int status = connection.getResponseCode();
        byte[] body;
        try (var stream = status < 400 ? connection.getInputStream() : connection.getErrorStream()) {
            body = stream == null ? new byte[0] : stream.readAllBytes();
        }
        return new Answer(status, connection.getContentType(), body, connection);
    }

    private Answer get(String path) throws IOException {
        return request("GET", path);
    }

    /* --------------------------------------------------------------- tests */

    @Test
    @DisplayName("a lambda route answers what its lambda returns")
    void lambdaRoutesAnswer() throws IOException {
        assertEquals("Hello world", get("/hello").text());
        assertEquals("Hello Pedro", get("/hello?name=Pedro").text());
        assertEquals(String.valueOf(Math.PI), get("/pi").text());
    }

    @Test
    void queryValuesAreDecoded() throws IOException {
        assertEquals("Hello Ana María", get("/hello?name=Ana%20Mar%C3%ADa").text());
        assertEquals("Hello Pedro", get("/hello?name=Pedro&language=en").text());
    }

    @Test
    @DisplayName("a handler can change the content type")
    void handlerCanSetTheContentType() throws IOException {
        Answer answer = get("/json");

        assertEquals(200, answer.status());
        assertEquals("application/json; charset=utf-8", answer.contentType());
        assertEquals("{\"ok\":true}", answer.text());
    }

    @Test
    void servesTheStaticPageAndItsResources() throws IOException {
        Answer page = get("/");
        assertEquals(200, page.status());
        assertEquals("text/html; charset=utf-8", page.contentType());
        assertTrue(page.text().contains("<title>tdse-webframework</title>"));

        assertEquals("text/css; charset=utf-8", get("/styles.css").contentType());
        assertEquals("application/javascript; charset=utf-8", get("/app.js").contentType());
        assertEquals(200, get("/index.html").status());
    }

    @Test
    @DisplayName("a binary resource arrives with its own type and length")
    void servesImages() throws IOException {
        Answer logo = get("/images/logo.png");

        assertEquals(200, logo.status());
        assertEquals("image/png", logo.contentType());
        assertTrue(logo.body().length > 1000);
        assertEquals(logo.body().length, logo.connection().getContentLength());
        // The PNG signature must survive untouched.
        assertEquals((byte) 0x89, logo.body()[0]);

        assertEquals("image/jpeg", get("/images/architecture.jpg").contentType());
    }

    @Test
    void unknownResourceIs404() throws IOException {
        Answer answer = get("/unknown");

        assertEquals(404, answer.status());
        assertEquals("text/plain; charset=utf-8", answer.contentType());
        assertEquals("404 Not Found", answer.text());
        assertEquals(404, get("/nope.html").status());
    }

    @Test
    void unsupportedMethodIsRejected() throws IOException {
        Answer answer = request("POST", "/hello");

        assertEquals(405, answer.status());
        assertEquals("GET", answer.connection().getHeaderField("Allow"));
    }

    @Test
    @DisplayName("a failing lambda becomes a 500 and the server keeps running")
    void failingHandlerDoesNotStopTheServer() throws IOException {
        assertEquals(500, get("/boom").status());
        assertEquals(200, get("/pi").status());
    }

    @Test
    void malformedRequestDoesNotStopTheServer() throws IOException {
        try (Socket socket = new Socket("localhost", URI.create(baseUrl).getPort())) {
            socket.getOutputStream().write("THIS IS NOT HTTP\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            String answer = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(answer.startsWith("HTTP/1.1 400"), answer);
        }
        assertEquals(200, get("/pi").status());
    }

    @Test
    void handlesManyConsecutiveRequests() throws IOException {
        for (String path : List.of("/", "/pi", "/hello?name=a", "/styles.css", "/app.js",
                "/images/logo.png", "/json", "/unknown", "/pi", "/hello")) {
            int status = get(path).status();
            assertTrue(status == 200 || status == 404, path + " -> " + status);
        }
    }

    @Test
    @DisplayName("/shutdown answers first and only then the loop ends")
    void shutdownIsGraceful() throws Exception {
        Answer answer = get("/shutdown");

        // The response was written completely before the server closed anything.
        assertEquals(200, answer.status());
        assertEquals("Server will stop after this response.", answer.text());

        serverThread.join(3000);
        assertFalse(serverThread.isAlive(), "the accept loop should have ended");
        assertFalse(server.isRunning());
    }
}
