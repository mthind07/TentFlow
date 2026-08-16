package com.mthind.tentflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class BookingConfiguration {

    @Bean
    Clock tentFlowClock() {
        return Clock.system(ZoneId.of("America/Toronto"));
    }
}
