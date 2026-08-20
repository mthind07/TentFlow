package com.mthind.tentflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//describes TentFlow in the generated Swagger page
@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI tentFlowOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ))
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth")
                )
                .info(
                        new Info()
                                .title("TentFlow API")
                                .version("0.4.0")
                                .description(
                                        "Secured PostgreSQL-backed tent inventory, "
                                                + "reservation, maintenance, and "
                                                + "waitlist API."
                                )
                );
    }
}