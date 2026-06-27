package com.hisaberp.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:hisab-erp}")
    private String appName;

    @Value("${app.version:0.1.0-SNAPSHOT}")
    private String appVersion;

    @Value("${app.api.public-url:http://localhost:8080}")
    private String publicUrl;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Hisab ERP API")
                        .description("Multi-tenant SaaS Hisab ERP for Mauritanian retail/wholesale.")
                        .version(appVersion)
                        .contact(new Contact().name("Hisab ERP team").email("noreply@hisaberp.local"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url(publicUrl).description("Configured server")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token (15-minute lifetime). Obtain via /api/v1/auth/login.")));
    }
}
