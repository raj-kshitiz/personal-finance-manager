package com.pfm.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;

/**
 * Direct unit tests for {@link JsonAuthenticationEntryPoint} and {@link JsonAccessDeniedHandler},
 * verifying both emit the unified {@code ErrorResponse} JSON shape.
 */
class SecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("JsonAuthenticationEntryPoint writes a 401 JSON ErrorResponse body")
    void authenticationEntryPointWritesUnauthorizedBody() throws Exception {
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthenticationException authException = new BadCredentialsException("no auth header");

        entryPoint.commence(request, response, authException);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("error").asText()).isEqualTo("Unauthorized");
        assertThat(body.get("message").asText()).isEqualTo("Authentication required");
        assertThat(body.get("path").asText()).isEqualTo("/api/transactions");
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.has("details")).isFalse();
    }

    @Test
    @DisplayName("JsonAccessDeniedHandler writes a 403 JSON ErrorResponse body")
    void accessDeniedHandlerWritesForbiddenBody() throws Exception {
        JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/goals/5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AccessDeniedException accessDeniedException = new AccessDeniedException("not allowed");

        handler.handle(request, response, accessDeniedException);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("message").asText()).isEqualTo("Access denied");
        assertThat(body.get("path").asText()).isEqualTo("/api/goals/5");
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.has("details")).isFalse();
    }
}
