package com.atlas.shared;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentação da API (OpenAPI/Swagger UI).
 * <p>
 * Declara o esquema Bearer JWT, o que faz o Swagger UI exibir o botão "Authorize", e marca com
 * cadeado todas as rotas protegidas. As rotas públicas ficam sem cadeado.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    /** Prefixos de rotas públicas; precisa acompanhar as regras do SecurityConfig. */
    static final List<String> PUBLIC_PATH_PREFIXES = List.of("/auth");

    @Bean
    OpenAPI atlasOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Atlas Flight Booking API")
                        .version("0.1.0")
                        .description("Assistente de reservas de voos com IA. "
                                + "Faça login em /auth/login, clique em Authorize e cole o accessToken."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    /** Exige o esquema Bearer em toda operação, exceto nas rotas públicas. */
    @Bean
    OpenApiCustomizer bearerSecurityCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                boolean isPublic = PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
                if (!isPublic) {
                    item.readOperations().forEach(operation ->
                            operation.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME)));
                }
            });
        };
    }
}
