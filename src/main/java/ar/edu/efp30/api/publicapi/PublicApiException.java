package ar.edu.efp30.api.publicapi;

import org.springframework.http.HttpStatus;

import java.util.List;

public class PublicApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final String code;
    private final List<FieldError> fieldErrors;
    private final long retryAfterSeconds;

    private PublicApiException(HttpStatus status, String title, String code, String detail, List<FieldError> fieldErrors) {
        this(status, title, code, detail, fieldErrors, 0);
    }

    private PublicApiException(HttpStatus status, String title, String code, String detail,
                               List<FieldError> fieldErrors, long retryAfterSeconds) {
        super(detail);
        this.status = status;
        this.title = title;
        this.code = code;
        this.fieldErrors = List.copyOf(fieldErrors);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static PublicApiException badRequest(String field, String message) {
        return new PublicApiException(HttpStatus.BAD_REQUEST, "Solicitud inválida", "VALIDATION_ERROR",
                "Revisá los campos indicados.", List.of(new FieldError(field, message)));
    }

    public static PublicApiException notFound() {
        return new PublicApiException(HttpStatus.NOT_FOUND, "No encontrado", "NOT_FOUND",
                "El recurso no existe o no está disponible.", List.of());
    }

    public static PublicApiException unauthorized() {
        return new PublicApiException(HttpStatus.UNAUTHORIZED, "No autenticado", "UNAUTHENTICATED",
                "La sesión no es válida.", List.of());
    }

    public static PublicApiException payloadTooLarge() {
        return new PublicApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Archivo demasiado grande", "FILE_TOO_LARGE",
                "El archivo supera el límite permitido.", List.of());
    }

    public static PublicApiException unsupportedMediaType() {
        return new PublicApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de archivo no permitido", "UNSUPPORTED_MEDIA_TYPE",
                "El contenido no coincide con los formatos aceptados.", List.of());
    }
    public static PublicApiException conflict(String code, String detail) {
        return new PublicApiException(HttpStatus.CONFLICT, "Conflicto", code, detail, List.of());
    }

    public static PublicApiException rateLimited(long retryAfterSeconds) {
        return new PublicApiException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos", "RATE_LIMITED",
                "Esperá antes de volver a intentar.", List.of(), retryAfterSeconds);
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

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public record FieldError(String field, String message) {
    }
}
