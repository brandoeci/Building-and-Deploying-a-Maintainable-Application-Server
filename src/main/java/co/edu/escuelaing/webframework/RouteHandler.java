package co.edu.escuelaing.webframework;

/**
 * What the application registers with {@code get(path, handler)}.
 *
 * <p>It is a functional interface, so a lambda works:
 * {@code get("/pi", (req, resp) -> String.valueOf(Math.PI))}.</p>
 */
@FunctionalInterface
public interface RouteHandler {

    /**
     * Produces the body of the answer.
     *
     * @param req  the incoming request, with its query values
     * @param resp the answer, in case the handler needs another status or type
     * @return the text of the response body
     * @throws Exception anything the handler fails with; the server turns it
     *                   into a 500 instead of dying
     */
    String handle(Request req, Response resp) throws Exception;
}
