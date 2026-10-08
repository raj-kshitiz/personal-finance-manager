package com.pfm.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Fails fast with an actionable message when the {@code postgres} profile is active but the
 * database environment variables are missing.
 *
 * <p>Without this check an unset {@code DB_URL} reaches the JDBC driver as the literal string
 * {@code ${DB_URL}}, and the deploy log shows
 * {@code Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}} buried in a Hikari
 * stack trace — which does not tell the operator that they simply forgot to set a variable.</p>
 */
@Configuration
@Profile("postgres")
public class PostgresEnvironmentValidator implements InitializingBean {

    private static final String[] REQUIRED = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"};

    private final Environment environment;

    /**
     * Creates the validator.
     *
     * @param environment the Spring environment to read the variables from
     */
    public PostgresEnvironmentValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Verifies that every required database variable is present and non-blank.
     *
     * @throws IllegalStateException naming every missing variable
     */
    @Override
    public void afterPropertiesSet() {
        StringBuilder missing = new StringBuilder();
        for (String name : REQUIRED) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                missing.append(missing.isEmpty() ? "" : ", ").append(name);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "The 'postgres' profile is active but these environment variables are not set: "
                            + missing + ". Set them (DB_URL for example "
                            + "jdbc:postgresql://host:5432/dbname) or run without the postgres "
                            + "profile to use the in-memory H2 database.");
        }
    }
}
