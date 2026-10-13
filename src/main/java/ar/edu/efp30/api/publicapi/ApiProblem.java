package ar.edu.efp30.api.publicapi;

import java.util.List;

public record ApiProblem(String type, String title, int status, String detail, String instance, String code,
                         List<PublicApiException.FieldError> fieldErrors) {
}
