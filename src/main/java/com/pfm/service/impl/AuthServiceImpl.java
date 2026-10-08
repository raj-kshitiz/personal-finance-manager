package com.pfm.service.impl;

import com.pfm.dto.request.LoginRequest;
import com.pfm.dto.request.RegisterRequest;
import com.pfm.dto.response.RegisterResponse;
import com.pfm.entity.User;
import com.pfm.exception.ConflictException;
import com.pfm.exception.UnauthorizedException;
import com.pfm.repository.UserRepository;
import com.pfm.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Default {@link AuthService} implementation: registration, session-based login and logout.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    /**
     * Creates the service.
     *
     * @param userRepository             persists and loads {@link User} entities
     * @param passwordEncoder            encodes new passwords with BCrypt
     * @param authenticationManager      authenticates username/password credentials
     * @param securityContextRepository  explicitly persists the security context into the session
     */
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           AuthenticationManager authenticationManager,
                           SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        String normalizedUsername = request.username().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ConflictException("Username is already registered");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(normalizedUsername, passwordHash, request.fullName(), request.phoneNumber());
        User saved = userRepository.save(user);
        return new RegisterResponse("User registered successfully", saved.getId());
    }

    @Override
    public void login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String normalizedUsername = request.username().toLowerCase(Locale.ROOT);
        Authentication authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(normalizedUsername, request.password());
        Authentication authenticationResult;
        try {
            authenticationResult = authenticationManager.authenticate(authenticationRequest);
        } catch (org.springframework.security.core.AuthenticationException ex) {
            throw new UnauthorizedException("Invalid username or password");
        }

        // Session-fixation protection: assign a new session id before establishing the new identity.
        sessionAuthenticationStrategy.onAuthentication(authenticationResult, httpRequest, httpResponse);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationResult);
        SecurityContextHolder.setContext(context);

        // Spring Security 6 does not persist the context into the session automatically for
        // programmatic authentication; it must be done explicitly.
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
    }

    @Override
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        Cookie expiredCookie = new Cookie("JSESSIONID", null);
        expiredCookie.setPath(contextPathOrRoot(httpRequest));
        expiredCookie.setMaxAge(0);
        expiredCookie.setHttpOnly(true);
        expiredCookie.setSecure(httpRequest.isSecure());
        httpResponse.addCookie(expiredCookie);
    }

    private String contextPathOrRoot(HttpServletRequest httpRequest) {
        String contextPath = httpRequest.getContextPath();
        return (contextPath == null || contextPath.isEmpty()) ? "/" : contextPath;
    }
}
