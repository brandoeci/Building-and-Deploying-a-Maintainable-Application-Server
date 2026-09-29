package co.edu.escuelaing.webframework;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticFileServiceTest {

    @TempDir
    Path sandbox;

    private StaticFileService service;
    private byte[] pngBytes;

    @BeforeEach
    void setUp() throws IOException {
        Path webroot = Files.createDirectories(sandbox.resolve("webroot/images"));
        Files.writeString(webroot.getParent().resolve("index.html"), "<html>home</html>",
                StandardCharsets.UTF_8);
        pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, (byte) 0xFF, 0x00};
        Files.write(webroot.resolve("logo.png"), pngBytes);
        Files.writeString(sandbox.resolve("secret.txt"), "do not serve me", StandardCharsets.UTF_8);

        service = new StaticFileService();
        service.setDirectory(webroot.getParent());
    }

    @Test
    void rootServesTheWelcomeFile() throws Exception {
        StaticFileService.StaticResource resource = service.find("/");

        assertNotNull(resource);
        assertEquals("text/html; charset=utf-8", resource.contentType());
        assertEquals("<html>home</html>", new String(resource.bytes(), StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a binary resource keeps its bytes untouched")
    void imagesAreServedAsBytes() throws Exception {
        StaticFileService.StaticResource resource = service.find("/images/logo.png");

        assertEquals("image/png", resource.contentType());
        assertArrayEquals(pngBytes, resource.bytes());
    }

    @Test
    @DisplayName("a missing file is not an error here: the server answers 404")
    void missingFileReturnsNull() throws Exception {
        assertNull(service.find("/nope.html"));
        assertNull(service.find("/data.exe"));
    }

    @Test
    void traversalIsRejected() {
        for (String attack : new String[]{"/../secret.txt", "/images/../../secret.txt",
                "/./../secret.txt", "/..\\secret.txt", "/.env"}) {
            assertThrows(StaticFileService.ForbiddenException.class, () -> service.find(attack), attack);
        }
    }

    @Test
    void normalizeResolvesWelcomeFiles() throws Exception {
        assertEquals("index.html", StaticFileService.normalize("/"));
        assertEquals("index.html", StaticFileService.normalize(""));
        assertEquals("images/index.html", StaticFileService.normalize("/images/"));
        assertEquals("images/logo.png", StaticFileService.normalize("/images//logo.png"));
        assertEquals("images/logo.png", StaticFileService.normalize("/./images/logo.png"));
    }

    @Test
    @DisplayName("the classpath folder is configured with staticfiles()")
    void rootIsConfigurable() {
        StaticFileService fromJar = new StaticFileService();

        fromJar.setRoot("/webroot");
        assertEquals("/webroot/", fromJar.getRoot());

        fromJar.setRoot("assets");
        assertEquals("/assets/", fromJar.getRoot());
    }

    @Test
    void readsTheRealWebrootFromTheClasspath() throws Exception {
        StaticFileService fromJar = new StaticFileService();
        fromJar.setRoot("/webroot");

        assertNotNull(fromJar.find("/index.html"));
        assertTrue(fromJar.find("/images/logo.png").bytes().length > 0);
    }
}
