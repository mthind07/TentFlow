package com.mthind.tentflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//describes TentFlow in the generated Swagger page
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI tentFlowOpenApi() {
        return new OpenAPI().info(
                new Info()
                        .title("TentFlow API")
                        .version("0.2.0")
                        .description(
                                "In-memory tent inventory, "
                                        + "reservation, maintenance, "
                                        + "and waitlist API."
                        )
        );
    }
}