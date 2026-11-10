package com.bynexlabs.bankingbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the actuator and API-docs settings (roadmap 9.x), so a later edit can't quietly expose
 * internal endpoints. Access itself (ADMINISTRATOR only for /actuator/**) is set in SecurityConfig.
 */
class ActuatorAndApiDocsSettingsTest {

    private static Properties applicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ActuatorAndApiDocsSettingsTest.class.getResourceAsStream("/application.properties")) {
            assertThat(in).as("application.properties on the classpath").isNotNull();
            properties.load(in);
        }
        return properties;
    }

    @Test
    void onlyHealthAndInfoAreExposedUnderActuator() throws IOException {
        Properties properties = applicationProperties();
        assertThat(properties.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info");
        // SecurityConfig protects /actuator/**; moving the base path would move the endpoints out from under it.
        assertThat(properties.getProperty("management.endpoints.web.base-path")).isEqualTo("/actuator");
        assertThat(properties.getProperty("management.endpoint.health.show-details")).isEqualTo("when-authorized");
    }

    @Test
    void apiDocsAreOffUnlessExplicitlyEnabled() throws IOException {
        Properties properties = applicationProperties();
        assertThat(properties.getProperty("springdoc.api-docs.enabled")).isEqualTo("${API_DOCS_ENABLED:false}");
        assertThat(properties.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("${API_DOCS_ENABLED:false}");
    }

    @Test
    void openApiDocumentsTheJwtBearerToken() {
        OpenAPI openApi = new OpenApiConfig().bankingBackendOpenApi();

        SecurityScheme scheme = openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME);
        assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        assertThat(openApi.getSecurity()).anySatisfy(requirement ->
                assertThat(requirement).containsKey(OpenApiConfig.BEARER_SCHEME));
    }
}
