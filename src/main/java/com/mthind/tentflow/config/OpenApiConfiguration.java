package com.mthind.tentflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI tentFlowOpenApi() {
        return new OpenAPI().info(
                new Info()
                        .title("TentFlow API")
                        .version("0.3.0")
                        .description(
                                "PostgreSQL-backed tent inventory, reservation, "
                                        + "maintenance, and waitlist API."
                        )
        );
    }
}