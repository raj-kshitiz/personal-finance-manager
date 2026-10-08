package com.pfm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfm.dto.request.LoginRequest;
import com.pfm.dto.request.RegisterRequest;
import com.pfm.dto.response.RegisterResponse;
import com.pfm.entity.User;
import com.pfm.exception.ConflictException;
import com.pfm.exception.UnauthorizedException;
import com.pfm.repository.UserRepository;
import com.pfm.service.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Mockito unit tests for {@link AuthServiceImpl} covering registration, login and logout.
 */
class AuthServiceImplTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationManager authenticationManager;
    private SecurityContextRepository securityContextRepository;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        authenticationManager = mock(AuthenticationManager.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        authService = new AuthServiceImpl(userRepository, passwordEncoder, authenticationManager,
                securityContextRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("register lower-cases the username, BCrypt-encodes the password and returns the new id")
    void registerSuccess() {
        RegisterRequest request = new RegisterRequest("John.Doe@Example.com", "password123", "John Doe",
                "+1234567890");
        when(userRepository.existsByUsername("john.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        RegisterResponse response = authService.register(request);

        assertThat(response.message()).isEqualTo("User registered successfully");
        assertThat(response.userId()).isEqualTo(42L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("john.doe@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed-value");
        verify(passwordEncoder).encode("password123");
    }

    @Test
    @DisplayName("register rejects a username that already exists, case-insensitively")
    void registerDuplicateUsername() {
        RegisterRequest request = new RegisterRequest("Taken@Example.com", "password123", "Name", "+1234567890");
        when(userRepository.existsByUsername("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("login success saves the security context, persists it to the session and clears on logout")
    void loginSuccessSavesContextAndChangesSessionId() {
        LoginRequest request = new LoginRequest("User@Example.com", "password123");
        Authentication resultAuthentication =
                UsernamePasswordAuthenticationToken.authenticated("user@example.com", "password123", null);
        when(authenticationManager.authenticate(any())).thenReturn(resultAuthentication);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        HttpSession originalSession = httpRequest.getSession(true);
        String originalSessionId = originalSession.getId();

        authService.login(request, httpRequest, httpResponse);

        ArgumentCaptor<org.springframework.security.core.context.SecurityContext> contextCaptor =
                ArgumentCaptor.forClass(org.springframework.security.core.context.SecurityContext.class);
        verify(securityContextRepository).saveContext(contextCaptor.capture(), eq(httpRequest), eq(httpResponse));
        assertThat(contextCaptor.getValue().getAuthentication()).isEqualTo(resultAuthentication);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(resultAuthentication);
        // MockHttpServletRequest's session is replaced by ChangeSessionIdAuthenticationStrategy
        // (session-fixation protection), so a brand new session id must have been issued.
        assertThat(httpRequest.getSession(false).getId()).isNotEqualTo(originalSessionId);

        ArgumentCaptor<org.springframework.security.authentication.UsernamePasswordAuthenticationToken> authCaptor =
                ArgumentCaptor.forClass(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(authCaptor.capture());
        assertThat(authCaptor.getValue().getPrincipal()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("login with bad credentials throws UnauthorizedException (mapped to 401)")
    void loginBadCredentials() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        assertThatThrownBy(() -> authService.login(request, httpRequest, httpResponse))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("logout invalidates the session, clears the security context and expires the cookie")
    void logoutInvalidatesSessionAndClearsContext() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        org.springframework.mock.web.MockHttpSession session =
                (org.springframework.mock.web.MockHttpSession) httpRequest.getSession(true);

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated("user@example.com", "x", null);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        authService.logout(httpRequest, httpResponse);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        jakarta.servlet.http.Cookie expiredCookie = httpResponse.getCookie("JSESSIONID");
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("logout with no active session does not throw")
    void logoutWithoutSessionIsNoOp() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authService.logout(httpRequest, httpResponse);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("logout over a secure (HTTPS) request marks the expired cookie Secure")
    void logoutOverHttpsMarksCookieSecure() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSecure(true);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authService.logout(httpRequest, httpResponse);

        jakarta.servlet.http.Cookie expiredCookie = httpResponse.getCookie("JSESSIONID");
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getSecure()).isTrue();
        assertThat(expiredCookie.getMaxAge()).isZero();
        assertThat(expiredCookie.isHttpOnly()).isTrue();
        assertThat(expiredCookie.getPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("logout over a plain HTTP request leaves the expired cookie not Secure")
    void logoutOverHttpLeavesCookieNotSecure() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSecure(false);
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authService.logout(httpRequest, httpResponse);

        jakarta.servlet.http.Cookie expiredCookie = httpResponse.getCookie("JSESSIONID");
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getSecure()).isFalse();
    }

    @Test
    @DisplayName("logout from a non-root context path scopes the expired cookie's Path to that context")
    void logoutWithNonRootContextPathScopesCookiePath() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setContextPath("/app");
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        authService.logout(httpRequest, httpResponse);

        jakarta.servlet.http.Cookie expiredCookie = httpResponse.getCookie("JSESSIONID");
        assertThat(expiredCookie).isNotNull();
        assertThat(expiredCookie.getPath()).isEqualTo("/app");
        assertThat(expiredCookie.getMaxAge()).isZero();
    }
}
