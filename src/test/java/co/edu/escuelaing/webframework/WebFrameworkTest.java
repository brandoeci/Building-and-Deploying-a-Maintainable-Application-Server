package co.edu.escuelaing.webframework;

import static co.edu.escuelaing.webframework.WebFramework.get;
import static co.edu.escuelaing.webframework.WebFramework.staticfiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The API the application uses, exactly as the lab asks for it. */
class WebFrameworkTest {

    @BeforeEach
    void cleanRoutes() {
        WebFramework.reset();
    }

    @Test
    @DisplayName("get() registers a service without touching the server loop")
    void getRegistersTheRoute() throws Exception {
        get("/hello", (req, resp) -> "Hello " + req.getValue("name"));
        get("/pi", (req, resp) -> String.valueOf(Math.PI));

        Router router = WebFramework.server().router();
        assertEquals(2, router.size());
        assertNotNull(router.find("GET", "/hello"));

        Request request = RequestTest.get("/hello?name=Pedro");
        assertEquals("Hello Pedro", router.find("GET", "/hello").handler().handle(request, new Response()));
    }

    @Test
    void staticfilesConfiguresTheFolder() {
        staticfiles("/webroot");

        assertEquals("/webroot/", WebFramework.server().staticFiles().getRoot());
        assertTrue(WebFramework.server().staticFiles().describeSource().contains("webroot"));
    }

    @Test
    @DisplayName("the port comes from PORT, with 8080 as the local default")
    void portComesFromTheEnvironment() {
        String configured = System.getenv("PORT");

        if (configured == null || configured.isBlank()) {
            assertEquals(8080, HttpServer.portFromEnvironment());
        } else {
            assertEquals(Integer.parseInt(configured.trim()), HttpServer.portFromEnvironment());
        }
    }

    @Test
    void aServerThatIsNotListeningHasNoPort() {
        assertEquals(-1, new HttpServer().port());
    }

    @Test
    void resetForgetsTheRoutes() {
        get("/hello", (req, resp) -> "hi");
        WebFramework.reset();

        assertNull(WebFramework.server().router().find("GET", "/hello"));
    }
}
