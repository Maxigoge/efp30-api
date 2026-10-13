package ar.edu.efp30.api.publicapi;

import org.springframework.http.HttpStatus;

import java.util.List;

public class PublicApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final String code;
    private final List<FieldError> fieldErrors;

    private PublicApiException(HttpStatus status, String title, String code, String detail, List<FieldError> fieldErrors) {
        super(detail);
        this.status = status;
        this.title = title;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static PublicApiException badRequest(String field, String message) {
        return new PublicApiException(HttpStatus.BAD_REQUEST, "Solicitud inválida", "VALIDATION_ERROR",
                "Revisá los campos indicados.", List.of(new FieldError(field, message)));
    }

    public static PublicApiException notFound() {
        return new PublicApiException(HttpStatus.NOT_FOUND, "No encontrado", "NOT_FOUND",
                "El recurso no existe o no está disponible.", List.of());
    }

    public static PublicApiException institutionNotConfigured() {
        return new PublicApiException(HttpStatus.SERVICE_UNAVAILABLE, "Información no disponible",
                "INSTITUTION_NOT_CONFIGURED", "La información institucional todavía no está configurada.", List.of());
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String code() {
        return code;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String message) {
    }
}
