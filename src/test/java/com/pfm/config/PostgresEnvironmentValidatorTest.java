package com.pfm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Unit tests for {@link PostgresEnvironmentValidator}. */
class PostgresEnvironmentValidatorTest {

    private static MockEnvironment environmentWith(String url, String username, String password) {
        MockEnvironment environment = new MockEnvironment();
        if (url != null) {
            environment.setProperty("DB_URL", url);
        }
        if (username != null) {
            environment.setProperty("DB_USERNAME", username);
        }
        if (password != null) {
            environment.setProperty("DB_PASSWORD", password);
        }
        return environment;
    }

    @Test
    @DisplayName("a fully configured environment passes")
    void fullyConfigured() {
        PostgresEnvironmentValidator validator = new PostgresEnvironmentValidator(
                environmentWith("jdbc:postgresql://localhost:5432/pfm", "pfm", "secret"));

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a single missing variable is named in the failure")
    void singleMissingVariable() {
        PostgresEnvironmentValidator validator = new PostgresEnvironmentValidator(
                environmentWith(null, "pfm", "secret"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageNotContaining("DB_USERNAME")
                .hasMessageContaining("postgres");
    }

    @Test
    @DisplayName("every missing variable is listed, not just the first")
    void allMissingVariablesListed() {
        PostgresEnvironmentValidator validator =
                new PostgresEnvironmentValidator(environmentWith(null, null, null));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("a blank value counts as missing, not as configured")
    void blankCountsAsMissing() {
        PostgresEnvironmentValidator validator =
                new PostgresEnvironmentValidator(environmentWith("   ", "pfm", "secret"));

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL");
    }

    @Test
    @DisplayName("the message tells the operator what to do next")
    void messageIsActionable() {
        PostgresEnvironmentValidator validator =
                new PostgresEnvironmentValidator(environmentWith(null, null, null));

        assertThatThrownBy(validator::afterPropertiesSet)
                .satisfies(thrown -> assertThat(thrown.getMessage())
                        .contains("jdbc:postgresql://")
                        .contains("H2"));
    }
}
