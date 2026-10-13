package ar.edu.efp30.api.publicapi;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PublicApiException.class)
    ResponseEntity<ApiProblem> handlePublicApi(PublicApiException exception, HttpServletRequest request) {
        ResponseEntity<ApiProblem> response = problem(exception.status(), exception.title(), exception.getMessage(),
                exception.code(), exception.fieldErrors(), request);
        if (exception.retryAfterSeconds() == 0) {
            return response;
        }
        return ResponseEntity.status(response.getStatusCode())
                .header("Retry-After", Long.toString(exception.retryAfterSeconds()))
                .contentType(response.getHeaders().getContentType())
                .body(response.getBody());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiProblem> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                   HttpServletRequest request) {
        return badRequest(exception.getName(), "El valor tiene un formato inválido.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiProblem> handleMissingParameter(MissingServletRequestParameterException exception,
                                                       HttpServletRequest request) {
        return badRequest(exception.getParameterName(), "El parámetro es obligatorio.", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiProblem> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                     HttpServletRequest request) {
        return badRequest("body", "El cuerpo de la solicitud no tiene un formato válido.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> handleBeanValidation(MethodArgumentNotValidException exception,
                                                     HttpServletRequest request) {
        List<PublicApiException.FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new PublicApiException.FieldError(error.getField(), error.getDefaultMessage()))
                .distinct()
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", "Revisá los campos indicados.",
                "VALIDATION_ERROR", fieldErrors, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API failure for {}", request.getRequestURI(), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "No se pudo completar.",
                "INTERNAL_ERROR", List.of(), request);
    }

    private ResponseEntity<ApiProblem> badRequest(String field, String message, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", "Revisá los campos indicados.",
                "VALIDATION_ERROR", List.of(new PublicApiException.FieldError(field, message)), request);
    }

    private ResponseEntity<ApiProblem> problem(HttpStatus status, String title, String detail, String code,
                                                List<PublicApiException.FieldError> fieldErrors,
                                                HttpServletRequest request) {
        ApiProblem body = new ApiProblem("about:blank", title, status.value(), detail,
                request.getRequestURI(), code, fieldErrors);
        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf("application/problem+json"))
                .body(body);
    }
}
