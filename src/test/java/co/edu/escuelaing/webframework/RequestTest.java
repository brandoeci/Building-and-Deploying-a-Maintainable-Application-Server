package co.edu.escuelaing.webframework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestTest {

    static Request get(String target) throws BadRequestException {
        return Request.of("GET " + target + " HTTP/1.1", Map.of());
    }

    @Test
    void readsTheQueryValue() throws Exception {
        Request request = get("/hello?name=Pedro");

        assertEquals("GET", request.getMethod());
        assertEquals("/hello", request.getPath());
        assertEquals("Pedro", request.getValue("name"));
    }

    @Test
    void readsSeveralQueryValues() throws Exception {
        Request request = get("/hello?name=Pedro&language=en");

        assertEquals("Pedro", request.getValue("name"));
        assertEquals("en", request.getValue("language"));
        assertEquals(2, request.getValues().size());
    }

    @Test
    @DisplayName("a parameter that was not sent returns null and does not fail")
    void missingParameterIsNull() throws Exception {
        Request request = get("/hello");

        assertNull(request.getValue("name"));
        assertEquals("world", request.getValue("name", "world"));
        assertTrue(request.getValues().isEmpty());
    }

    @Test
    void decodesEscapedValues() throws Exception {
        assertEquals("Ana María", get("/hello?name=Ana%20Mar%C3%ADa").getValue("name"));
        assertEquals("Juan Perez", get("/hello?name=Juan+Perez").getValue("name"));
    }

    @Test
    @DisplayName("a plus sign in a path is a plus sign, not a space")
    void plusInPathIsLiteral() throws Exception {
        assertEquals("/a+b.png", get("/a+b.png").getPath());
    }

    @Test
    void rejectsAMalformedRequestLine() {
        assertThrows(BadRequestException.class, () -> get(""));
        assertThrows(BadRequestException.class,
                () -> Request.of("GET /hello", Map.of()));
        assertThrows(BadRequestException.class,
                () -> Request.of("GET /hello FTP/1.0", Map.of()));
        assertThrows(BadRequestException.class,
                () -> Request.of("GET http://other/ HTTP/1.1", Map.of()));
    }

    @Test
    void rejectsABrokenEscape() {
        assertThrows(BadRequestException.class, () -> get("/hello?name=%ZZ"));
    }
}
