package ee.smit.agent.api;

import ee.smit.agent.agent.ModelUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", "Päring ei ole korrektne."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_JSON", "Päringu JSON ei ole korrektne."));
    }

    @ExceptionHandler(ModelUnavailableException.class)
    public ResponseEntity<ApiError> handleModelUnavailable(ModelUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("MODEL_UNAVAILABLE", "OpenAI teenus ei ole praegu saadaval."));
    }
}
