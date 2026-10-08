package com.pfm.exception;

import com.pfm.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception raised by the API into the unified {@link ErrorResponse} body.
 *
 * <p>Status mapping:</p>
 * <ul>
 *   <li>{@link BadRequestException}, bean validation failures, unreadable JSON, bad path/query
 *       types and missing parameters &rarr; 400</li>
 *   <li>{@link UnauthorizedException} and Spring Security authentication failures &rarr; 401</li>
 *   <li>{@link ForbiddenOperationException} &rarr; 403</li>
 *   <li>{@link ResourceNotFoundException} and unmapped routes &rarr; 404</li>
 *   <li>unsupported HTTP method &rarr; 405, unsupported media type &rarr; 415</li>
 *   <li>{@link ConflictException} and unique-constraint violations &rarr; 409</li>
 *   <li>anything else &rarr; 500 with a generic message, stack trace logged</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles business rule violations.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 400 Bad Request
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    /**
     * Handles authentication problems raised by the application itself.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 401 Unauthorized
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    /**
     * Handles failed credential checks.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 401 Unauthorized with a generic message
     */
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class, LockedException.class,
            AuthenticationException.class})
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex,
                                                              HttpServletRequest request) {
        LOG.debug("Authentication failure: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request, null);
    }

    /**
     * Handles forbidden operations such as deleting a default category.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 403 Forbidden
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenOperationException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    /**
     * Handles missing resources.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 404 Not Found
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    /**
     * Handles duplicate-resource conflicts.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 409 Conflict
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    /**
     * Handles bean validation failures on {@code @RequestBody} arguments.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 400 Bad Request including one detail line per rejected field
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<String> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> details.add(error.getObjectName() + ": " + error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
    }

    /**
     * Handles bean validation failures on path and query parameters.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 400 Bad Request
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
    }

    /**
     * Handles malformed JSON, wrong value types and unparseable dates or enums in the request body.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        LOG.debug("Unreadable request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "Malformed request body: check that all fields have the correct type and that dates use "
                        + "the YYYY-MM-DD format",
                request, null);
    }

    /**
     * Handles non-numeric path variables and unparseable query parameters.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'", request, null);
    }

    /**
     * Handles missing mandatory query parameters.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 400 Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'",
                request, null);
    }

    /**
     * Handles unique-constraint violations that slipped past the explicit duplicate checks.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        LOG.debug("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request, null);
    }

    /**
     * Handles calls to an existing path with an unsupported HTTP method.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method " + ex.getMethod() + " is not supported for "
                + "this endpoint", request, null);
    }

    /**
     * Handles request bodies sent with an unsupported content type.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 415 Unsupported Media Type
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type must be application/json", request, null);
    }

    /**
     * Handles requests to unmapped routes.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 404 Not Found
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoHandler(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint found for this path", request, null);
    }

    /**
     * Last-resort handler. Logs the stack trace and returns a generic message so that no internal
     * detail leaks to the client.
     *
     * @param ex      the exception
     * @param request the current request
     * @return 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
                                                List<String> details) {
        return ResponseEntity.status(status)
                .body(ErrorResponseFactory.of(status, message, request.getRequestURI(), details));
    }
}
