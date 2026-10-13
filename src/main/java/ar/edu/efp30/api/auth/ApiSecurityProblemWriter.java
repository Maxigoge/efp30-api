package ar.edu.efp30.api.auth;

import ar.edu.efp30.api.publicapi.ApiProblem;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ApiSecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiSecurityProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "No autenticado", "La sesión no es válida.",
                "UNAUTHENTICATED", request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        boolean csrf = exception instanceof MissingCsrfTokenException || exception instanceof InvalidCsrfTokenException;
        if (csrf) {
            write(response, HttpStatus.FORBIDDEN, "Solicitud rechazada", "Volvé a pedir el token CSRF.",
                    "CSRF_INVALID", request);
        } else {
            write(response, HttpStatus.FORBIDDEN, "Acceso denegado", "No tenés permiso.", "FORBIDDEN", request);
        }
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail, String code,
                       HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.valueOf("application/problem+json").toString());
        response.setCharacterEncoding("UTF-8");
        ApiProblem body = new ApiProblem("about:blank", title, status.value(), detail,
                request.getRequestURI(), code, List.of());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
