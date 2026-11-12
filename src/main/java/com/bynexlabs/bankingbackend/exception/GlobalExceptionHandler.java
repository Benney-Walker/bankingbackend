package com.bynexlabs.bankingbackend.exception;

import com.bynexlabs.bankingbackend.Utility.KycUploadRules;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error the API returns uses one body shape, which the desktop and mobile clients rely on:
 * {@code {timestamp, status, error, message, path}}. Validation failures add an {@code errors}
 * map of field to message. CustomAuthEntryPoint and CustomAccessDeniedHandler write the same
 * shape for 401/403 responses raised by the security filters, which never reach this class.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex, HttpServletRequest request) {
        // Expected outcomes (duplicates, missing records), so they stay out of the normal log level.
        log.debug("{} on {} {}: {}", ex.getStatus().value(), request.getMethod(), request.getRequestURI(), ex.getMessage());
        return error(ex.getStatus(), ex.getMessage(), request);
    }

    // An inactive account gets the same answer as a wrong password, so the login endpoint
    // doesn't reveal which staff IDs exist or have been deactivated.
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<Map<String, Object>> handleBadCredentials(HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid ID or password", request);
    }

    // @PreAuthorize denials are thrown inside the controller call, so they arrive here rather than
    // at CustomAccessDeniedHandler. Without this mapping the catch-all would turn them into 500s.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(HttpServletRequest request) {
        String principal = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
        log.warn("Access denied for '{}' on {} {}", principal, request.getMethod(), request.getRequestURI());
        return error(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request);
    }

    // Since Spring Framework 6.1, unknown paths raise NoResourceFoundException rather than
    // NoHandlerFoundException; without handling it they fell through to the 500 handler.
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "The requested resource was not found", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ResponseEntity<Map<String, Object>> response = error(HttpStatus.BAD_REQUEST, fieldErrors, request);
        response.getBody().put("errors", fieldErrors);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Request body is missing or malformed", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "Missing required part '" + ex.getRequestPartName() + "'", request);
    }

    // The servlet multipart limit (spring.servlet.multipart.max-file-size) is hit before the
    // controller runs; without this it fell through to a 500.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(HttpServletRequest request) {
        return error(PayloadTooLargeException.STATUS, KycUploadRules.TOO_LARGE_MESSAGE, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not supported for this endpoint", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex, HttpServletRequest request) {
        // Full detail goes to the log only; the client gets a generic message so internals
        // (SQL, stack traces, provider errors) are never exposed.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error! Contact developer", request);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, Object message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
