package co.edu.escuelaing.webframework;

/**
 * One registered service: the method and path that identify it, and the lambda
 * that answers it.
 */
public record Route(String method, String path, RouteHandler handler) {

    public boolean matches(String method, String path) {
        return this.method.equalsIgnoreCase(method) && this.path.equals(path);
    }
}
