package com.atlas.assistant.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Indexa as políticas na subida, se houver chave da OpenAI. Falha aqui não derruba a aplicação: o
 * assistente continua respondendo, só sem a base de políticas atualizada.
 */
@Component
class PolicyIngestor implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestor.class);

    private final PolicyStore policies;
    private final AssistantProperties props;
    private final boolean configured;

    PolicyIngestor(PolicyStore policies, AssistantProperties props,
                   @Value("${spring.ai.openai.api-key:missing}") String apiKey) {
        this.policies = policies;
        this.props = props;
        this.configured = AssistantService.isConfigured(apiKey);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.policies().ingestOnStartup()) {
            return;
        }
        if (!configured) {
            log.warn("OPENAI_API_KEY not set: assistant policies were not indexed");
            return;
        }
        try {
            int indexed = policies.ingest();
            log.info("Assistant policies up to date ({} files indexed)", indexed);
        } catch (Exception e) {
            log.warn("Could not index assistant policies: {}", e.getClass().getSimpleName());
        }
    }
}
