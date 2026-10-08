package com.pfm.security;

import com.pfm.entity.User;
import com.pfm.repository.UserRepository;
import java.util.Collections;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a {@link UserDetails} by username for Spring Security's authentication machinery.
 *
 * <p>Usernames are e-mail addresses and are always stored lower-cased, so look-ups lower-case the
 * supplied value first. The returned principal carries no authorities (this application has a
 * single role), and its username is the stored lower-cased e-mail address, which is exactly what
 * {@link SecurityContextCurrentUserProvider} uses to re-load the {@link User} entity.</p>
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Creates the service.
     *
     * @param userRepository repository used to load users
     */
    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads the user identified by the given username.
     *
     * @param username the username, matched case-insensitively
     * @return a {@link UserDetails} wrapping the stored password hash and no authorities
     * @throws UsernameNotFoundException if no such user exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.toLowerCase(Locale.ROOT);
        User user = userRepository.findByUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("No user with username " + normalized));
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPasswordHash(), Collections.emptyList());
    }
}
