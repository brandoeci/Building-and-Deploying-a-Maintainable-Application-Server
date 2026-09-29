package co.edu.escuelaing.webframework;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The directory of the building: it knows which lambda answers each path.
 *
 * <p>This is what replaces the chain of {@code if (path.equals(...))} that used
 * to live inside the connection loop. Registering a new service now means
 * adding an entry here, and the server code does not change.</p>
 */
public class Router {

    /** Key is "METHOD path", so the same path could later exist for another method. */
    private final Map<String, Route> routes = new LinkedHashMap<>();

    /** Registers a service. Registering the same path twice replaces the first one. */
    public void add(String method, String path, RouteHandler handler) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("A route path must start with '/': " + path);
        }
        if (handler == null) {
            throw new IllegalArgumentException("A route needs a handler: " + path);
        }
        String normalized = normalize(path);
        routes.put(key(method, normalized), new Route(method.toUpperCase(Locale.ROOT), normalized, handler));
    }

    /** @return the route registered for this method and path, or {@code null} */
    public Route find(String method, String path) {
        return routes.get(key(method, normalize(path)));
    }

    public boolean has(String method, String path) {
        return find(method, path) != null;
    }

    public Collection<Route> routes() {
        return routes.values();
    }

    public int size() {
        return routes.size();
    }

    /** Forgets every route. Used between tests. */
    public void clear() {
        routes.clear();
    }

    /** "/hello/" and "/hello" are the same service. */
    private static String normalize(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String key(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path;
    }
}
