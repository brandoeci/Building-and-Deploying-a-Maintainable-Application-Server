package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The API the application uses: {@code staticfiles()}, {@code get()},
 * {@code start()} and {@code stop()}.
 *
 * <p>It is a thin facade over one {@link HttpServer}. The point is that the
 * application never sees a socket, and the server never sees a service:</p>
 *
 * <pre>
 * import static co.edu.escuelaing.webframework.WebFramework.*;
 *
 * staticfiles("/webroot");
 * get("/pi", (req, resp) -&gt; String.valueOf(Math.PI));
 * start();
 * </pre>
 */
public final class WebFramework {

    private static final HttpServer SERVER = new HttpServer();

    private WebFramework() {
    }

    /**
     * Says where the static resources live inside the jar, for example
     * {@code "/webroot"}.
     *
     * <p>If the {@code STATIC_FILES_PATH} environment variable points to a folder
     * on disk, that folder wins: handy to edit the page without rebuilding, and
     * to mount the files beside the container.</p>
     */
    public static void staticfiles(String root) {
        SERVER.staticFiles().setRoot(root);

        String external = System.getenv("STATIC_FILES_PATH");
        if (external != null && !external.isBlank()) {
            if (Files.isDirectory(Paths.get(external.trim()))) {
                SERVER.staticFiles().setDirectory(Paths.get(external.trim()));
            } else {
                System.err.println("STATIC_FILES_PATH is not a folder: '" + external + "'. Ignored.");
            }
        }
    }

    /**
     * Registers a GET service.
     *
     * @param path    the exact path, for example {@code "/hello"}
     * @param handler the lambda that answers it
     */
    public static void get(String path, RouteHandler handler) {
        SERVER.router().add("GET", path, handler);
    }

    /** Starts the server on the port given by {@code PORT}, or 8080. */
    public static void start() throws IOException {
        start(HttpServer.portFromEnvironment());
    }

    /** Starts the server on an explicit port. Blocks until {@link #stop()}. */
    public static void start(int port) throws IOException {
        SERVER.start(port);
    }

    /**
     * Asks the server to stop.
     *
     * <p>Nothing is killed here: the flag is turned off, the response being
     * prepared is still sent, the connection is closed normally and only then
     * does the accept loop end.</p>
     */
    public static void stop() {
        SERVER.stop();
    }

    /** @return the port in use, or -1 when the server is not listening */
    public static int port() {
        return SERVER.port();
    }

    public static boolean isRunning() {
        return SERVER.isRunning();
    }

    /** The server behind the facade. Used by the tests. */
    static HttpServer server() {
        return SERVER;
    }

    /** Forgets every registered route. Used between tests. */
    static void reset() {
        SERVER.router().clear();
    }
}
