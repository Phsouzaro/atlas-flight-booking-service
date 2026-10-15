package com.atlas.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void declaresBearerJwtScheme() {
        OpenAPI api = config.atlasOpenApi();

        assertThat(api.getComponents().getSecuritySchemes()).containsKey(OpenApiConfig.BEARER_SCHEME);
        assertThat(api.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_SCHEME).getScheme())
                .isEqualTo("bearer");
    }

    @Test
    void requiresBearerOnProtectedRoutesOnly() {
        OpenAPI api = config.atlasOpenApi().paths(new Paths()
                .addPathItem("/me", new PathItem().get(new Operation()))
                .addPathItem("/auth/login", new PathItem().post(new Operation())));

        config.bearerSecurityCustomizer().customise(api);

        assertThat(api.getPaths().get("/me").getGet().getSecurity()).hasSize(1);
        assertThat(api.getPaths().get("/auth/login").getPost().getSecurity()).isNullOrEmpty();
    }
}
