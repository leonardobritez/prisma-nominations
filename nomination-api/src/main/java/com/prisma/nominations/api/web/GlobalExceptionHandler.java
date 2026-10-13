package com.prisma.nominations.api.web;

import com.prisma.nominations.api.service.IdempotencyConflictException;
import com.prisma.nominations.common.correlation.CorrelationContext;
import com.prisma.nominations.common.error.ApiError;
import com.prisma.nominations.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Traduce excepciones a un modelo de error consistente con codigos estables.
 * Nunca expone stack traces ni datos sensibles.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Set<String> MISSING_FIELD_ANNOTATIONS = Set.of("NotBlank", "NotNull", "NotEmpty");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ErrorCode code = errors.stream().anyMatch(e -> e.code().equals(ErrorCode.MISSING_FIELD.name()))
                ? ErrorCode.MISSING_FIELD
                : ErrorCode.INVALID_FORMAT;
        return build(HttpStatus.BAD_REQUEST, code, "La solicitud contiene datos invalidos.", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FORMAT,
                "El cuerpo de la solicitud es invalido o no pudo interpretarse.", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FORMAT,
                "Parametro invalido: " + ex.getName(), request, List.of());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyConflictException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_CONFLICT, ex.getMessage(),
                request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR,
                "Se produjo un error interno. Use el correlationId para el seguimiento.", request, List.of());
    }

    private ApiError.FieldError toFieldError(FieldError fieldError) {
        String annotation = fieldError.getCode() == null ? "" : fieldError.getCode();
        String code = MISSING_FIELD_ANNOTATIONS.contains(annotation)
                ? ErrorCode.MISSING_FIELD.name()
                : ErrorCode.INVALID_FORMAT.name();
        return new ApiError.FieldError(fieldError.getField(), code, fieldError.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message,
                                           HttpServletRequest request, List<ApiError.FieldError> errors) {
        ApiError body = new ApiError(
                Instant.now().toString(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                CorrelationContext.getOrCreateCorrelationId(),
                errors
        );
        return ResponseEntity.status(status).body(body);
    }
}
