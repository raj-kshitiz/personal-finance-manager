package com.pfm.config;

import com.pfm.security.AppUserDetailsService;
import com.pfm.security.JsonAccessDeniedHandler;
import com.pfm.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Central Spring Security configuration: stateful, session-cookie based authentication with a
 * JSON-only error surface.
 *
 * <p>CSRF protection is disabled. This is a deliberate trade-off: the API is consumed by non-browser
 * clients (curl, automated test scripts) that authenticate with a session cookie but never render
 * any HTML form from this application, so there is no cross-site request forgery vector of the kind
 * CSRF protection defends against. Session fixation protection on login plus {@code HttpOnly} /
 * {@code SameSite=Lax} cookies cover the realistic risk instead.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppUserDetailsService userDetailsService;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    /**
     * Creates the configuration.
     *
     * @param userDetailsService       loads users by username for authentication
     * @param authenticationEntryPoint JSON 401 handler for unauthenticated access
     * @param accessDeniedHandler      JSON 403 handler for authenticated-but-denied access
     */
    public SecurityConfig(AppUserDetailsService userDetailsService,
                          JsonAuthenticationEntryPoint authenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler) {
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * BCrypt password encoder used both to hash new passwords and to verify credentials at login.
     *
     * @return a {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Explicit {@link SecurityContextRepository} bean so {@code AuthServiceImpl} can persist the
     * {@link org.springframework.security.core.context.SecurityContext} into the session after
     * programmatic authentication, which Spring Security 6 no longer does automatically.
     *
     * @return an {@link HttpSessionSecurityContextRepository}
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Exposes the {@link AuthenticationManager} built from the configured
     * {@link AppUserDetailsService} and {@link PasswordEncoder}, for use by the login flow.
     *
     * @param configuration Spring Security's authentication configuration
     * @return the authentication manager
     * @throws Exception if the manager cannot be built
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Registers the {@link DaoAuthenticationProvider} wiring the user details service to the
     * password encoder, so the {@link AuthenticationManager} above can authenticate by username and
     * password.
     *
     * @param passwordEncoder the BCrypt encoder bean; taken as a parameter rather than injected
     *                        into the constructor, which would make this configuration class
     *                        depend on a bean it defines itself
     * @return the configured authentication provider
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Defines the HTTP security rules: public registration/login/health/error endpoints, everything
     * else requires authentication; stateful sessions created only when required; CSRF disabled;
     * JSON entry point/access-denied handler; HTTP Basic and form login disabled.
     *
     * @param http                      the security builder
     * @param securityContextRepository the explicit context repository bean above
     * @return the built filter chain
     * @throws Exception if the chain cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    SecurityContextRepository securityContextRepository)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
