package com.pfm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfm.dto.request.LoginRequest;
import com.pfm.dto.request.RegisterRequest;
import com.pfm.dto.response.RegisterResponse;
import com.pfm.exception.ConflictException;
import com.pfm.exception.UnauthorizedException;
import com.pfm.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.stream.Stream;

/**
 * {@code @WebMvcTest} slice tests for {@link AuthController}.
 *
 * <p>Security filters are disabled here ({@code addFilters = false}): wiring the real
 * {@link com.pfm.config.SecurityConfig} into this slice would require standing up the full
 * authentication provider chain (user details service, password encoder, JPA repositories), which
 * defeats the purpose of a focused controller slice test. Instead this class asserts request
 * validation, status codes and response/error bodies at the controller boundary, and the
 * authentication/authorization rules themselves (session save, bad-credentials -&gt; 401, logout
 * clearing the context) are covered by {@code AuthServiceImplTest}.</p>
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/auth/register returns 201 with message and userId")
    void registerSuccess() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new RegisterResponse("User registered successfully", 7L));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "jane@example.com",
                                  "password": "password123",
                                  "fullName": "Jane Doe",
                                  "phoneNumber": "+1234567890"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 for a duplicate username")
    void registerDuplicate() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("Username is already registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "jane@example.com",
                                  "password": "password123",
                                  "fullName": "Jane Doe",
                                  "phoneNumber": "+1234567890"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of("missing username", """
                        {"password": "password123", "fullName": "Jane Doe", "phoneNumber": "+1234567890"}
                        """),
                Arguments.of("missing password", """
                        {"username": "jane@example.com", "fullName": "Jane Doe", "phoneNumber": "+1234567890"}
                        """),
                Arguments.of("bad email", """
                        {"username": "not-an-email", "password": "password123", "fullName": "Jane Doe", "phoneNumber": "+1234567890"}
                        """),
                Arguments.of("short password", """
                        {"username": "jane@example.com", "password": "short1", "fullName": "Jane Doe", "phoneNumber": "+1234567890"}
                        """),
                Arguments.of("bad phone", """
                        {"username": "jane@example.com", "password": "password123", "fullName": "Jane Doe", "phoneNumber": "123"}
                        """));
    }

    @ParameterizedTest(name = "{0} -> 400")
    @MethodSource("invalidRegistrations")
    @DisplayName("POST /api/auth/register returns 400 for each invalid field")
    void registerValidationFailures(String caseName, String body) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 with a success message")
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "jane@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"));

        verify(authService).login(any(LoginRequest.class), any(HttpServletRequest.class),
                any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 for bad credentials")
    void loginBadCredentials() throws Exception {
        doThrow(new UnauthorizedException("Invalid username or password"))
                .when(authService).login(any(LoginRequest.class), any(HttpServletRequest.class),
                        any(HttpServletResponse.class));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "jane@example.com", "password": "wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Invalid username or password")));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 400 when a required field is missing")
    void loginMissingField() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "jane@example.com"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/logout returns 200 with a success message")
    void logoutSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        verify(authService).logout(any(HttpServletRequest.class), any(HttpServletResponse.class));
    }
}
