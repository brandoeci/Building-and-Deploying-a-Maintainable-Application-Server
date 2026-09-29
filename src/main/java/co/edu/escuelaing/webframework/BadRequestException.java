package co.edu.escuelaing.webframework;

/** The request line or its escaping could not be understood. Answers 400. */
public class BadRequestException extends Exception {

    public BadRequestException(String message) {
        super(message);
    }
}
