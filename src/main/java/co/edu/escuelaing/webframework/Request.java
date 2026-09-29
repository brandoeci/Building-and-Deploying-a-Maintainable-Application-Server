package co.edu.escuelaing.webframework;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the handler receives: the method, the path and the query values of one
 * request. It is read only; handlers never write through it.
 */
public class Request {

    private final String method;
    private final String path;
    private final String rawQuery;
    private final Map<String, String> values;
    private final Map<String, String> headers;

    Request(String method, String path, String rawQuery,
            Map<String, String> values, Map<String, String> headers) {
        this.method = method;
        this.path = path;
        this.rawQuery = rawQuery;
        this.values = Collections.unmodifiableMap(values);
        this.headers = Collections.unmodifiableMap(headers);
    }

    /**
     * Reads a query-string value.
     *
     * <p>A parameter that was not sent returns {@code null}; asking for it is
     * never an error, so a handler can decide its own default.</p>
     */
    public String getValue(String name) {
        return values.get(name);
    }

    /** Same as {@link #getValue(String)} but with a default for the missing case. */
    public String getValue(String name, String fallback) {
        String value = values.get(name);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public String getMethod() {
        return method;
    }

    /** The path without the query string, for example {@code /hello}. */
    public String getPath() {
        return path;
    }

    public String getRawQuery() {
        return rawQuery;
    }

    /** @return a header by case-insensitive name, or {@code null} */
    public String getHeader(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return method + " " + path + (rawQuery.isEmpty() ? "" : "?" + rawQuery);
    }

    /* ------------------------------------------------------------- parsing */

    /**
     * Builds a request out of the request line and the headers already read
     * from the socket.
     *
     * @throws BadRequestException when the request line is malformed
     */
    static Request of(String requestLine, Map<String, String> headers) throws BadRequestException {
        String[] parts = requestLine.split(" ");
        if (parts.length != 3 || !parts[2].startsWith("HTTP/") || !parts[1].startsWith("/")) {
            throw new BadRequestException("Malformed request line: " + requestLine);
        }
        String method = parts[0].toUpperCase(Locale.ROOT);
        String target = parts[1];

        int q = target.indexOf('?');
        String rawPath = q < 0 ? target : target.substring(0, q);
        String rawQuery = q < 0 ? "" : target.substring(q + 1);

        return new Request(method, decode(rawPath, false), rawQuery, parseQuery(rawQuery), headers);
    }

    /** Splits {@code name=Pedro&language=en}; a repeated name keeps the first value. */
    static Map<String, String> parseQuery(String rawQuery) throws BadRequestException {
        Map<String, String> values = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            values.putIfAbsent(decode(name, true), decode(value, true));
        }
        return values;
    }

    /**
     * Decodes {@code %XX} escapes. In a query string {@code '+'} is a space; in a
     * path it is a literal plus sign.
     */
    static String decode(String value, boolean plusIsSpace) throws BadRequestException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '%') {
                if (i + 2 >= value.length()) {
                    throw new BadRequestException("Truncated escape in URL");
                }
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    throw new BadRequestException("Invalid escape in URL");
                }
                bytes.write((hi << 4) + lo);
                i += 2;
            } else if (c == '+' && plusIsSpace) {
                bytes.write(' ');
            } else {
                byte[] raw = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(raw, 0, raw.length);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
