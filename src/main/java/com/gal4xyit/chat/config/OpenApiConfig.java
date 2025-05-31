package com.gal4xyit.chat.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Super Chach API", version = "v1", description = "Super CHACH SUPER MEGA API"))
@SecurityScheme(
        name = "oauth2_keycloak_implicit_flow",
        type = SecuritySchemeType.OAUTH2,
        flows = @OAuthFlows(
                implicit = @OAuthFlow(
                        authorizationUrl = "http://localhost:8180/realms/chat-app-realm/protocol/openid-connect/auth"
                )
        )
)
public class OpenApiConfig {
}