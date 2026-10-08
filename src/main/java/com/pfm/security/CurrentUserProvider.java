package com.pfm.security;

import com.pfm.entity.User;

/**
 * Resolves the authenticated {@link User} for the current request. Injected into the service layer
 * so that services never touch the Spring Security API directly and stay trivially mockable.
 */
public interface CurrentUserProvider {

    /**
     * Returns the authenticated user.
     *
     * @return the authenticated user, never {@code null}
     * @throws com.pfm.exception.UnauthorizedException if there is no authenticated user, or the
     *                                                 authenticated principal no longer exists
     */
    User getCurrentUser();

    /**
     * Convenience accessor for the authenticated user's id.
     *
     * @return the authenticated user's id
     * @throws com.pfm.exception.UnauthorizedException if there is no authenticated user
     */
    default Long getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
