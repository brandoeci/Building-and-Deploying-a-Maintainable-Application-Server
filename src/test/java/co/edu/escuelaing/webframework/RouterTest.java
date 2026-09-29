package co.edu.escuelaing.webframework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouterTest {

    private final Router router = new Router();

    @Test
    void findsTheRegisteredLambda() throws Exception {
        router.add("GET", "/pi", (req, resp) -> String.valueOf(Math.PI));

        Route route = router.find("GET", "/pi");

        assertNotNull(route);
        assertEquals("/pi", route.path());
        assertEquals(String.valueOf(Math.PI), route.handler().handle(null, null));
    }

    @Test
    @DisplayName("the lambda receives the request and can read its values")
    void handlerReceivesTheRequest() throws Exception {
        router.add("GET", "/hello", (req, resp) -> "Hello " + req.getValue("name"));

        Request request = RequestTest.get("/hello?name=Pedro");

        assertEquals("Hello Pedro", router.find("GET", "/hello").handler().handle(request, new Response()));
    }

    @Test
    void unknownPathHasNoRoute() {
        router.add("GET", "/pi", (req, resp) -> "3.14");

        assertNull(router.find("GET", "/unknown"));
        assertFalse(router.has("GET", "/unknown"));
        assertTrue(router.has("GET", "/pi"));
    }

    @Test
    void theMethodIsPartOfTheKey() {
        router.add("GET", "/hello", (req, resp) -> "hi");

        assertNull(router.find("POST", "/hello"));
        assertNotNull(router.find("get", "/hello"));
    }

    @Test
    @DisplayName("a trailing slash points to the same service")
    void trailingSlashIsIgnored() {
        router.add("GET", "/hello/", (req, resp) -> "hi");

        assertNotNull(router.find("GET", "/hello"));
        assertNotNull(router.find("GET", "/hello/"));
        assertEquals(1, router.size());
    }

    @Test
    void registeringTheSamePathTwiceReplacesIt() throws Exception {
        router.add("GET", "/hello", (req, resp) -> "first");
        router.add("GET", "/hello", (req, resp) -> "second");

        assertEquals(1, router.size());
        assertEquals("second", router.find("GET", "/hello").handler().handle(null, null));
    }

    @Test
    void rejectsInvalidRegistrations() {
        assertThrows(IllegalArgumentException.class, () -> router.add("GET", "hello", (q, s) -> ""));
        assertThrows(IllegalArgumentException.class, () -> router.add("GET", null, (q, s) -> ""));
        assertThrows(IllegalArgumentException.class, () -> router.add("GET", "/hello", null));
    }
}
