package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The archive of the building: it hands out the files that were already
 * prepared (page, stylesheet, script, images) when no lambda answers the path.
 *
 * <p>The folder is chosen with {@code staticfiles("/webroot")} and is read from
 * inside the jar, unless {@code STATIC_FILES_PATH} points to a folder on disk
 * (useful to change the page without rebuilding).</p>
 */
public class StaticFileService {

    private static final String WELCOME_FILE = "index.html";

    /** Classpath folder, always with a leading and a trailing slash. */
    private String classpathRoot = "/webroot/";

    /** When set, files are read from here instead of the classpath. */
    private Path directoryRoot;

    /**
     * Configures where the static resources live inside the jar, for example
     * {@code "/webroot"}.
     */
    public void setRoot(String root) {
        String value = (root == null || root.isBlank()) ? "/webroot" : root.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        this.classpathRoot = value;
    }

    /** Reads the files from a folder on disk instead of from the jar. */
    public void setDirectory(Path directory) {
        this.directoryRoot = directory == null ? null : directory.toAbsolutePath().normalize();
    }

    public String describeSource() {
        return directoryRoot != null ? directoryRoot.toString() : "classpath:" + classpathRoot;
    }

    public String getRoot() {
        return classpathRoot;
    }

    /**
     * Looks for one static resource.
     *
     * @param path the request path, already decoded
     * @return the file bytes and its content type, or {@code null} when there is
     *         no such resource (the caller answers 404)
     * @throws ForbiddenException when the path tries to leave the static folder
     */
    public StaticResource find(String path) throws ForbiddenException {
        String relative = normalize(path);
        String contentType = MimeTypes.forPath(relative);
        if (contentType == null) {
            return null;
        }
        byte[] bytes = directoryRoot != null ? readFromDirectory(relative) : readFromClasspath(relative);
        return bytes == null ? null : new StaticResource(relative, contentType, bytes);
    }

    private byte[] readFromClasspath(String relative) {
        try (InputStream in = StaticFileService.class.getResourceAsStream(classpathRoot + relative)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private byte[] readFromDirectory(String relative) throws ForbiddenException {
        Path resolved = directoryRoot.resolve(relative).normalize();
        // Second check: whatever the normalisation produced must stay inside the root.
        if (!resolved.startsWith(directoryRoot)) {
            throw new ForbiddenException("Access outside the static folder is not allowed");
        }
        if (!Files.isRegularFile(resolved)) {
            return null;
        }
        try {
            if (!resolved.toRealPath().startsWith(directoryRoot.toRealPath())) {
                throw new ForbiddenException("Access outside the static folder is not allowed");
            }
            return Files.readAllBytes(resolved);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Turns a request path into a safe file name inside the static folder.
     *
     * <p>A {@code ..} segment is rejected instead of resolved, and so are
     * backslashes, hidden files, NUL bytes and drive prefixes. The root path and
     * any folder become the welcome file.</p>
     */
    static String normalize(String path) throws ForbiddenException {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return WELCOME_FILE;
        }
        if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
            throw new ForbiddenException("Illegal character in path");
        }

        Deque<String> segments = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                throw new ForbiddenException("Path traversal is not allowed");
            }
            if (segment.startsWith(".") || segment.contains(":")) {
                throw new ForbiddenException("Illegal path segment");
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            return WELCOME_FILE;
        }
        String joined = String.join("/", segments);
        return path.endsWith("/") ? joined + "/" + WELCOME_FILE : joined;
    }

    /** One file found in the static folder. */
    public record StaticResource(String name, String contentType, byte[] bytes) {
    }

    /** The path tried to leave the static folder. Answers 403. */
    public static class ForbiddenException extends Exception {
        public ForbiddenException(String message) {
            super(message);
        }
    }
}
