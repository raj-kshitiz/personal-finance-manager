package com.pfm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the Personal Finance Manager REST API.
 */
@SpringBootApplication
public class PersonalFinanceManagerApplication {

    /**
     * Boots the Spring application context.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(PersonalFinanceManagerApplication.class, args);
    }
}
