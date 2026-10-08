package com.pfm.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson tuning.
 *
 * <ul>
 *   <li>Unknown properties in request bodies are ignored, so an extra {@code date} field on a
 *       transaction update is simply dropped rather than rejected.</li>
 *   <li>{@link java.math.BigDecimal} values are written in plain notation, so money never appears
 *       in scientific notation.</li>
 *   <li>Dates are parsed strictly. Request DTOs deliberately carry no {@code @JsonFormat} pattern:
 *       a pattern-based formatter resolves leniently and would silently accept
 *       {@code "2024-02-30"} as 29 February. Without one, Jackson uses
 *       {@link java.time.format.DateTimeFormatter#ISO_LOCAL_DATE}, which rejects impossible
 *       calendar dates outright.</li>
 *   <li>A number is never coerced into a date. Otherwise {@code {"date": 12345}} would be read as
 *       an epoch day and quietly become a real transaction date the client never asked for.</li>
 *   <li>An empty string is never coerced into {@code null} for a date or a number, so
 *       {@code {"date": ""}} is a 400 rather than a missing-field surprise.</li>
 * </ul>
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
        return builder -> {
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.featuresToEnable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
            builder.postConfigurer(mapper -> {
                mapper.coercionConfigFor(LogicalType.DateTime)
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
                mapper.coercionConfigFor(LogicalType.Float)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            });
        };
    }
}
