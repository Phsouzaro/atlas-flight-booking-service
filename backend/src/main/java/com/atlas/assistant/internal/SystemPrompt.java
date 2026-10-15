package com.atlas.assistant.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * System prompt versionado: o texto fica em {@code assistant/prompts/system-VERSAO.md} e a versão em uso
 * ({@code app.assistant.prompt-version}) é gravada em cada resposta. Mudar o comportamento do assistente é
 * criar uma versão nova, não editar a antiga.
 */
@Component
class SystemPrompt {

    static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final String version;
    private final String template;
    private final Clock clock;

    SystemPrompt(AssistantProperties props, Clock clock) {
        this.version = props.promptVersion();
        this.clock = clock;
        try {
            this.template = new ClassPathResource("assistant/prompts/system-" + version + ".md").getContentAsString(UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("System prompt version " + version + " not found", e);
        }
    }

    String version() {
        return version;
    }

    String render() {
        return template.replace("{today}", LocalDate.now(clock.withZone(ZONE)).toString());
    }
}
