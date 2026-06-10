package com.cgcpms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI (Swagger) configuration.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI cgcPmsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CGC-PMS API")
                        .description("Construction General Contracting Project Management System")
                        .version("1.0.0")
                        .contact(new Contact().name("CGC-PMS Team")));
    }
}
