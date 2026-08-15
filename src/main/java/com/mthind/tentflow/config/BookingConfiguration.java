package com.mthind.tentflow.config;

import com.mthind.tentflow.service.TentBookingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

//connects the pure Java Milestone 1 engine to Spring
@Configuration
public class BookingConfiguration {

    @Bean
    Clock tentFlowClock() {
        return Clock.system(
                ZoneId.of("America/Toronto")
        );
    }

    @Bean
    TentBookingService tentBookingService(
            Clock tentFlowClock
    ) {
        return new TentBookingService(tentFlowClock);
    }
}
