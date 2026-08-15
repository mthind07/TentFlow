package com.mthind.tentflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the TentFlow Spring Boot web application.
 */
@SpringBootApplication
public class TentFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                TentFlowApplication.class,
                args
        );
    }
}
