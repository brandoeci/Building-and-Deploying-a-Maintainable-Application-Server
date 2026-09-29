package co.edu.escuelaing.app;

import static co.edu.escuelaing.webframework.WebFramework.get;
import static co.edu.escuelaing.webframework.WebFramework.staticfiles;
import static co.edu.escuelaing.webframework.WebFramework.start;
import static co.edu.escuelaing.webframework.WebFramework.stop;

import co.edu.escuelaing.webframework.Response;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * The example application.
 *
 * <p>Everything this file does is configuration: where the static files are and
 * which lambda answers each path. There is not a single socket here, and the
 * framework does not know what any of these services do.</p>
 */
public class Application {

    public static void main(String[] args) throws Exception {
        staticfiles("/webroot");

        // Values that change per deployment come from the environment, never from
        // the source code.
        String greetingPrefix = System.getenv().getOrDefault("GREETING_PREFIX", "Hello");
        String environment = System.getenv().getOrDefault("APP_ENV", "development");

        get("/hello", (req, resp) -> {
            String name = req.getValue("name");
            if (name == null || name.isBlank()) {
                name = "world";
            }
            String language = req.getValue("language", "en");
            String greeting = greetingPrefix + " " + name;
            return "es".equalsIgnoreCase(language) ? "Hola " + name : greeting;
        });

        get("/pi", (req, resp) -> String.valueOf(Math.PI));

        get("/square", (req, resp) -> {
            String value = req.getValue("value");
            if (value == null || value.isBlank()) {
                resp.status(400);
                return "Send a number: /square?value=7";
            }
            try {
                double number = Double.parseDouble(value);
                return value + " squared is " + (number * number);
            } catch (NumberFormatException e) {
                resp.status(400);
                return "'" + value + "' is not a number";
            }
        });

        get("/time", (req, resp) -> {
            resp.type(Response.APPLICATION_JSON);
            ZonedDateTime now = ZonedDateTime.now();
            return "{\"time\":\"" + now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    + "\",\"zone\":\"" + now.getZone() + "\"}";
        });

        get("/env", (req, resp) -> {
            resp.type(Response.APPLICATION_JSON);
            // Only non sensitive configuration is shown here.
            return "{\"appEnv\":\"" + environment + "\",\"greetingPrefix\":\"" + greetingPrefix
                    + "\",\"port\":\"" + System.getenv().getOrDefault("PORT", "8080") + "\"}";
        });

        // The shutdown route only exists outside production, so the public
        // deployment cannot be stopped by anyone who knows the URL.
        if ("development".equalsIgnoreCase(environment)) {
            get("/shutdown", (req, resp) -> {
                stop();
                return "Server will stop after this response.";
            });
            System.out.println("APP_ENV=development: /shutdown is enabled");
        } else {
            System.out.println("APP_ENV=" + environment + ": /shutdown is NOT registered");
        }

        start();
    }
}
