package com.pfm.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson tuning: unknown properties in request bodies are ignored (so that an extra {@code date}
 * field on a transaction update is simply dropped), and {@link java.math.BigDecimal} values are
 * always written in plain notation so that money never appears in scientific notation.
 */
@Configuration
public class JacksonConfig {

    /**
     * Customises the auto-configured object mapper.
     *
     * @return the customizer
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToEnable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }
}
