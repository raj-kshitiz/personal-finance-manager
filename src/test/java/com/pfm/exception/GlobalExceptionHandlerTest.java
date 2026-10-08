package com.pfm.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.pfm.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Direct unit tests for {@link GlobalExceptionHandler}, covering every exception handler method
 * and the unified {@link ErrorResponse} body it produces.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }

    private void assertBasicShape(ResponseEntity<ErrorResponse> response, HttpStatus status, String path) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(status.value());
        assertThat(body.error()).isEqualTo(status.getReasonPhrase());
        assertThat(body.path()).isEqualTo(path);
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("BadRequestException maps to 400 with the exception message and no details")
    void badRequestException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new BadRequestException("amount must be positive"), request("/api/x"));

        assertBasicShape(response, HttpStatus.BAD_REQUEST, "/api/x");
        assertThat(response.getBody().message()).isEqualTo("amount must be positive");
        assertThat(response.getBody().details()).isNull();
    }

    @Test
    @DisplayName("UnauthorizedException maps to 401 with the exception message")
    void unauthorizedException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleUnauthorized(new UnauthorizedException("no session"), request("/api/y"));

        assertBasicShape(response, HttpStatus.UNAUTHORIZED, "/api/y");
        assertThat(response.getBody().message()).isEqualTo("no session");
    }

    @Test
    @DisplayName("ForbiddenOperationException maps to 403 with the exception message")
    void forbiddenOperationException() {
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(
                new ForbiddenOperationException("cannot delete default category"), request("/api/categories/Food"));

        assertBasicShape(response, HttpStatus.FORBIDDEN, "/api/categories/Food");
        assertThat(response.getBody().message()).isEqualTo("cannot delete default category");
    }

    @Test
    @DisplayName("ResourceNotFoundException maps to 404 with the exception message")
    void resourceNotFoundException() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("transaction not found"), request("/api/transactions/9"));

        assertBasicShape(response, HttpStatus.NOT_FOUND, "/api/transactions/9");
        assertThat(response.getBody().message()).isEqualTo("transaction not found");
    }

    @Test
    @DisplayName("ConflictException maps to 409 with the exception message")
    void conflictException() {
        ResponseEntity<ErrorResponse> response =
                handler.handleConflict(new ConflictException("username taken"), request("/api/auth/register"));

        assertBasicShape(response, HttpStatus.CONFLICT, "/api/auth/register");
        assertThat(response.getBody().message()).isEqualTo("username taken");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException maps to 400 with one details entry per field error plus global errors")
    void methodArgumentNotValidException() throws NoSuchMethodException {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "amount", "must be greater than 0"));
        bindingResult.addError(new FieldError("request", "date", "must not be null"));
        bindingResult.addError(new ObjectError("request", "start date must be before end date"));

        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", Object.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex, request("/api/transactions"));

        assertBasicShape(response, HttpStatus.BAD_REQUEST, "/api/transactions");
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        List<String> details = response.getBody().details();
        assertThat(details).containsExactlyInAnyOrder(
                "amount: must be greater than 0",
                "date: must not be null",
                "request: start date must be before end date");
    }

    @SuppressWarnings("unused")
    private void dummyMethod(Object arg) {
        // used only to obtain a MethodParameter above
    }

    @Test
    @DisplayName("ConstraintViolationException maps to 400 with one details entry per violation")
    void constraintViolationException() {
        ConstraintViolation<?> violation1 = mockViolation("listTransactions.categoryId", "must be positive");
        ConstraintViolation<?> violation2 = mockViolation("listTransactions.page", "must not be negative");
        ConstraintViolationException ex =
                new ConstraintViolationException(Set.of(violation1, violation2));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex, request("/api/transactions"));

        assertBasicShape(response, HttpStatus.BAD_REQUEST, "/api/transactions");
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().details()).containsExactlyInAnyOrder(
                "listTransactions.categoryId: must be positive",
                "listTransactions.page: must not be negative");
    }

    private ConstraintViolation<?> mockViolation(String propertyPath, String message) {
        ConstraintViolation<?> violation = Mockito.mock(ConstraintViolation.class);
        Path path = Mockito.mock(Path.class);
        Mockito.when(path.toString()).thenReturn(propertyPath);
        Mockito.when(violation.getPropertyPath()).thenReturn(path);
        Mockito.when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    @Test
    @DisplayName("HttpMessageNotReadableException maps to 400 with a format-hint message")
    void httpMessageNotReadableException() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error");

        ResponseEntity<ErrorResponse> response = handler.handleUnreadable(ex, request("/api/transactions"));

        assertBasicShape(response, HttpStatus.BAD_REQUEST, "/api/transactions");
        assertThat(response.getBody().message()).contains("Malformed request body");
        assertThat(response.getBody().details()).isNull();
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException maps to 400 naming the offending parameter")
    void methodArgumentTypeMismatchException() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", Object.class), 0);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Long.class, "categoryId", methodParameter,
                        new NumberFormatException("abc"));

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex, request("/api/transactions"));

        assertBasicShape(response, HttpStatus.BAD_REQUEST, "/api/transactions");
        assertThat(response.getBody().message()).isEqualTo("Invalid value for parameter 'categoryId'");
    }

    @Test
    @DisplayName("MissingServletRequestParameterException maps to 400 naming the missing parameter")
    void missingServletRequestParameterException() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("month", "int");

        ResponseEntity<ErrorResponse> response = handler.handleMissingParameter(ex, request("/api/reports/monthly"));

        assertBasicShape(response, HttpStatus.BAD_REQUEST, "/api/reports/monthly");
        assertThat(response.getBody().message()).isEqualTo("Missing required parameter 'month'");
    }

    @Test
    @DisplayName("DataIntegrityViolationException maps to 409 with a generic conflict message")
    void dataIntegrityViolationException() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("unique constraint violated");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(ex, request("/api/categories"));

        assertBasicShape(response, HttpStatus.CONFLICT, "/api/categories");
        assertThat(response.getBody().message()).isEqualTo("The request conflicts with existing data");
        assertThat(response.getBody().message()).doesNotContain("unique constraint violated");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException maps to 405 naming the offending method")
    void httpRequestMethodNotSupportedException() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException(HttpMethod.DELETE.name());

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request("/api/transactions"));

        assertBasicShape(response, HttpStatus.METHOD_NOT_ALLOWED, "/api/transactions");
        assertThat(response.getBody().message())
                .isEqualTo("HTTP method DELETE is not supported for this endpoint");
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException maps to 415")
    void httpMediaTypeNotSupportedException() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> response = handler.handleMediaType(ex, request("/api/transactions"));

        assertBasicShape(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "/api/transactions");
        assertThat(response.getBody().message()).isEqualTo("Content type must be application/json");
    }

    @Test
    @DisplayName("NoHandlerFoundException maps to 404")
    void noHandlerFoundException() {
        NoHandlerFoundException ex =
                new NoHandlerFoundException("GET", "/api/unknown", org.springframework.http.HttpHeaders.EMPTY);

        ResponseEntity<ErrorResponse> response = handler.handleNoHandler(ex, request("/api/unknown"));

        assertBasicShape(response, HttpStatus.NOT_FOUND, "/api/unknown");
        assertThat(response.getBody().message()).isEqualTo("No endpoint found for this path");
    }

    @Test
    @DisplayName("NoResourceFoundException maps to 404")
    void noResourceFoundException() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/static/missing.js");

        ResponseEntity<ErrorResponse> response = handler.handleNoHandler(ex, request("/static/missing.js"));

        assertBasicShape(response, HttpStatus.NOT_FOUND, "/static/missing.js");
        assertThat(response.getBody().message()).isEqualTo("No endpoint found for this path");
    }

    @Test
    @DisplayName("BadCredentialsException maps to 401 with a generic message that never echoes the username")
    void badCredentialsExceptionDoesNotLeakUsername() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials for user hacker@example.com");

        ResponseEntity<ErrorResponse> response = handler.handleAuthentication(ex, request("/api/auth/login"));

        assertBasicShape(response, HttpStatus.UNAUTHORIZED, "/api/auth/login");
        assertThat(response.getBody().message()).isEqualTo("Invalid username or password");
        assertThat(response.getBody().message()).doesNotContain("hacker@example.com");
        assertThat(response.getBody().message()).doesNotContain("Bad credentials");
    }

    @Test
    @DisplayName("Generic AuthenticationException also maps to 401 with the same generic message")
    void genericAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("some internal auth detail") {
        };

        ResponseEntity<ErrorResponse> response = handler.handleAuthentication(ex, request("/api/auth/login"));

        assertBasicShape(response, HttpStatus.UNAUTHORIZED, "/api/auth/login");
        assertThat(response.getBody().message()).isEqualTo("Invalid username or password");
        assertThat(response.getBody().message()).doesNotContain("some internal auth detail");
    }

    @Test
    @DisplayName("Catch-all Exception maps to 500 with a generic message, leaking no internal detail")
    void catchAllExceptionHidesInternalDetail() {
        RuntimeException ex = new IllegalStateException("database password is hunter2");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex, request("/api/anything"));

        assertBasicShape(response, HttpStatus.INTERNAL_SERVER_ERROR, "/api/anything");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("hunter2");
        assertThat(response.getBody().message()).doesNotContain("IllegalStateException");
        assertThat(response.getBody().details()).isNull();
        // The full body (not just the message field) must not leak the original exception message or type.
        String fullBody = response.getBody().toString();
        assertThat(fullBody).doesNotContain("hunter2");
        assertThat(fullBody).doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("details is omitted (null) when there are no field errors")
    void detailsOmittedWhenEmpty() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBadRequest(new BadRequestException("simple failure"), request("/api/x"));

        assertThat(response.getBody().details()).isNull();
    }
}
