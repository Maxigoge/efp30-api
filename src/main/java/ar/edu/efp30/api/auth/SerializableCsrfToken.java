package ar.edu.efp30.api.auth;

import org.springframework.security.web.csrf.CsrfToken;

import java.io.Serial;
import java.io.Serializable;

public record SerializableCsrfToken(String headerName, String parameterName, String token)
        implements CsrfToken, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getHeaderName() {
        return headerName;
    }

    @Override
    public String getParameterName() {
        return parameterName;
    }

    @Override
    public String getToken() {
        return token;
    }
}
