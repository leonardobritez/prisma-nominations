package com.prisma.nominations.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI nominationsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PRISMA - Nominations API")
                .version("v1")
                .description("API de nominacion de cuentas y tarjetas. Procesamiento asincronico con ABM.")
                .contact(new Contact().name("Equipo APIs")));
    }
}
