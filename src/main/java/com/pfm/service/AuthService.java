package com.pfm.service;

import com.pfm.dto.request.LoginRequest;
import com.pfm.dto.request.RegisterRequest;
import com.pfm.dto.response.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Registration, login and logout.
 */
public interface AuthService {

    /**
     * Registers a new user. The username is lower-cased before storage and the password is hashed
     * with BCrypt.
     *
     * @param request the validated registration payload
     * @return the confirmation message and the new user's id
     * @throws com.pfm.exception.ConflictException if the username is already taken
     *                                             (case-insensitive)
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * Authenticates the caller and establishes a session.
     *
     * <p>Implementations must authenticate case-insensitively, apply session fixation protection
     * (a new session id) and explicitly persist the {@code SecurityContext} into the session.</p>
     *
     * @param request      the validated login payload
     * @param httpRequest  the current request, used to create the session
     * @param httpResponse the current response, used to write the session cookie
     * @throws com.pfm.exception.UnauthorizedException if the credentials do not match
     */
    void login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);

    /**
     * Terminates the current session: invalidates it, clears the security context and expires the
     * session cookie, so that any later request carrying the old cookie receives 401.
     *
     * @param httpRequest  the current request
     * @param httpResponse the current response
     */
    void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse);
}
