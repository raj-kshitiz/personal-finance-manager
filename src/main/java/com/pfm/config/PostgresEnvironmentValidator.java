package com.pfm.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/**
 * Fails fast, with an actionable message, when the {@code postgres} profile is active but the
 * database environment variables are missing.
 *
 * <p>The check is published as a {@link BeanFactoryPostProcessor} from a {@code static} factory
 * method on purpose. Spring creates the {@code DataSource} and {@code EntityManagerFactory} very
 * early, and an ordinary validating bean is not guaranteed to be constructed before them — an
 * earlier attempt at this check never actually ran during a real boot. A bean factory
 * post-processor runs before any singleton is instantiated, so the check always wins that race.</p>
 *
 * <p>Without it, an unset {@code DB_URL} reaches the JDBC driver as the literal string
 * {@code ${DB_URL}} and the log shows
 * {@code Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}} buried in a
 * Hibernate/Hikari stack trace, which does not tell the operator that they simply forgot to set a
 * variable.</p>
 */
@Configuration
@Profile("postgres")
public class PostgresEnvironmentValidator {

    /** Environment variables that must be present when the {@code postgres} profile is active. */
    static final List<String> REQUIRED_VARIABLES = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD");

    /**
     * Registers the fail-fast check. Declared {@code static} so that Spring can invoke it without
     * instantiating the enclosing configuration class, which is a requirement for any
     * {@link BeanFactoryPostProcessor} factory method.
     *
     * @param environment the environment to inspect
     * @return a post-processor that validates the database configuration before any bean is created
     */
    @Bean
    public static BeanFactoryPostProcessor postgresEnvironmentCheck(Environment environment) {
        return new BeanFactoryPostProcessor() {
            @Override
            public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
                List<String> missing = missingVariables(environment);
                if (!missing.isEmpty()) {
                    throw new IllegalStateException(buildMessage(missing));
                }
            }
        };
    }

    /**
     * Collects the required variables that are absent or blank.
     *
     * @param environment the environment to inspect
     * @return the missing variable names, in declaration order
     */
    static List<String> missingVariables(Environment environment) {
        List<String> missing = new ArrayList<>();
        for (String name : REQUIRED_VARIABLES) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        return missing;
    }

    /**
     * Builds the operator-facing failure message.
     *
     * @param missing the missing variable names
     * @return a message naming every missing variable and what to do about it
     */
    static String buildMessage(List<String> missing) {
        return "The 'postgres' profile is active but these environment variables are not set: "
                + String.join(", ", missing)
                + ". Set them (DB_URL for example jdbc:postgresql://host:5432/dbname) or start "
                + "without the postgres profile to use the in-memory H2 database.";
    }
}
