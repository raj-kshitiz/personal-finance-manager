package com.pfm.security;

import com.pfm.entity.User;
import com.pfm.exception.UnauthorizedException;
import com.pfm.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Default {@link CurrentUserProvider} backed by the Spring Security context. The principal name is
 * the user's lower-cased e-mail address, which is used to load the {@link User} entity.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private final UserRepository userRepository;

    /**
     * Creates the provider.
     *
     * @param userRepository repository used to load the authenticated user
     */
    public SecurityContextCurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UnauthorizedException("Authentication required");
        }
        String username = authentication.getName().toLowerCase(Locale.ROOT);
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }
}
