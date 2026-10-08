package com.pfm.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the application {@link Clock}. Every "today" decision (future-date checks, default goal
 * start dates, progress cut-offs) goes through this bean so that tests can freeze time and so that
 * the deployed instance uses a well defined time zone rather than the container's default.
 */
@Configuration
public class ClockConfig {

    /**
     * Creates the application clock.
     *
     * @param timezone IANA time zone id, from {@code app.timezone} / {@code APP_TIMEZONE}
     * @return a clock fixed to the configured zone
     */
    @Bean
    public Clock applicationClock(@Value("${app.timezone:Asia/Kolkata}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
