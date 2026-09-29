package co.edu.escuelaing.webframework;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the handler can change about the answer, and what the server writes to
 * the socket.
 *
 * <p>A handler that only returns a string never touches this object; one that
 * needs another status or another content type does
 * {@code resp.status(404).type("text/plain")}.</p>
 *
 * <p>The body is always bytes so text and images use the same path and
 * {@code Content-Length} is the real size.</p>
 */
public class Response {

    public static final String TEXT_HTML = "text/html; charset=utf-8";
    public static final String TEXT_PLAIN = "text/plain; charset=utf-8";
    public static final String APPLICATION_JSON = "application/json; charset=utf-8";

    private int status = 200;
    // A lambda usually returns plain text; a handler that wants HTML says so.
    private String contentType = TEXT_PLAIN;
    private byte[] body = new byte[0];
    private final Map<String, String> headers = new LinkedHashMap<>();

    public Response status(int status) {
        this.status = status;
        return this;
    }

    public Response type(String contentType) {
        this.contentType = contentType;
        return this;
    }

    public Response header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public Response body(String body) {
        this.body = body.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public Response body(byte[] body) {
        this.body = body == null ? new byte[0] : body;
        return this;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    public int getContentLength() {
        return body.length;
    }

    /** Status line, headers, the mandatory blank line, and then the bytes. */
    void writeTo(OutputStream out) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        head.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC))).append("\r\n");
        head.append("Server: tdse-webframework/1.0\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        for (Map.Entry<String, String> header : headers.entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        // One connection at a time, so it is never kept alive.
        head.append("Connection: close\r\n\r\n");

        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }
}
