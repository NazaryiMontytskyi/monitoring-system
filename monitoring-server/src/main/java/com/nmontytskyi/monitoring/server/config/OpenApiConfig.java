package com.nmontytskyi.monitoring.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that customises the OpenAPI / Swagger UI metadata.
 *
 * <p>Sets the API title, version, description, and contact information displayed
 * at {@code /swagger-ui.html}. The actual endpoint documentation is generated
 * automatically by SpringDoc from the {@code @Operation} and {@code @Tag} annotations
 * on the REST controllers.
 *
 * @author Nazar Montytskyi
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI monitoringOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Monitoring Server API")
                        .version("1.0.0")
                        .description("REST API for microservices monitoring system"));
    }
}
