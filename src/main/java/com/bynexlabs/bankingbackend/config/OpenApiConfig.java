package com.bynexlabs.bankingbackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation (roadmap 9.x). Documents the JWT bearer token so "Authorize" in Swagger UI
 * sends it with every request. The docs are only served when API_DOCS_ENABLED=true.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI bankingBackendOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Credit Union Management API")
                        .description("Staff API. Every endpoint except /api/auth/** needs an access token "
                                + "from /api/auth/v1/login; each endpoint lists the roles allowed to call it.")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
