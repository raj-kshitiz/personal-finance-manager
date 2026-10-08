package com.pfm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
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
        BeanFactoryPostProcessor check = PostgresEnvironmentValidator.postgresEnvironmentCheck(
                environmentWith("jdbc:postgresql://localhost:5432/pfm", "pfm", "secret"));

        assertThatCode(() -> check.postProcessBeanFactory(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a single missing variable is named, and only that one")
    void singleMissingVariable() {
        BeanFactoryPostProcessor check = PostgresEnvironmentValidator.postgresEnvironmentCheck(
                environmentWith(null, "pfm", "secret"));

        assertThatThrownBy(() -> check.postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageNotContaining("DB_USERNAME")
                .hasMessageNotContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("every missing variable is listed, not just the first")
    void allMissingVariablesListed() {
        BeanFactoryPostProcessor check = PostgresEnvironmentValidator.postgresEnvironmentCheck(
                environmentWith(null, null, null));

        assertThatThrownBy(() -> check.postProcessBeanFactory(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("a blank value counts as missing, not as configured")
    void blankCountsAsMissing() {
        assertThat(PostgresEnvironmentValidator.missingVariables(
                environmentWith("   ", "pfm", "secret"))).containsExactly("DB_URL");
    }

    @Test
    @DisplayName("the message tells the operator what to do next")
    void messageIsActionable() {
        String message = PostgresEnvironmentValidator.buildMessage(
                PostgresEnvironmentValidator.REQUIRED_VARIABLES);

        assertThat(message)
                .contains("jdbc:postgresql://")
                .contains("H2")
                .contains("postgres");
    }
}
