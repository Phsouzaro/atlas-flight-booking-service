package com.atlas.assistant.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites e configuração do assistente.
 *
 * @param promptVersion        versão do system prompt; o arquivo é {@code assistant/prompts/system-<versão>.md}
 *                             e a versão fica gravada em cada resposta
 * @param maxInputChars        tamanho máximo da mensagem do usuário
 * @param maxOutputTokens      teto de tokens por resposta do modelo
 * @param maxToolCallsPerTurn  quantas tools o modelo pode chamar num turno, somando todas as rodadas
 * @param maxModelCallsPerTurn quantas vezes o modelo é chamado num turno (cada rodada de tools é uma)
 * @param historyWindow        quantas mensagens anteriores da conversa vão no prompt
 */
@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(String promptVersion, int maxInputChars, int maxOutputTokens, int maxToolCallsPerTurn,
                                  int maxModelCallsPerTurn, int historyWindow, Policies policies) {

    /**
     * @param ingestOnStartup indexar os documentos de política na subida (precisa de chave da OpenAI)
     * @param topK            quantos trechos a busca de política devolve
     */
    public record Policies(boolean ingestOnStartup, int topK) {

        public Policies {
            if (topK <= 0) {
                topK = 4;
            }
        }
    }

    public AssistantProperties {
        if (promptVersion == null || promptVersion.isBlank()) {
            promptVersion = "v1";
        }
        if (maxInputChars <= 0) {
            maxInputChars = 2000;
        }
        if (maxOutputTokens <= 0) {
            maxOutputTokens = 800;
        }
        if (maxToolCallsPerTurn <= 0) {
            maxToolCallsPerTurn = 6;
        }
        if (maxModelCallsPerTurn <= 0) {
            maxModelCallsPerTurn = 4;
        }
        if (historyWindow <= 0) {
            historyWindow = 20;
        }
        if (policies == null) {
            policies = new Policies(true, 4);
        }
    }
}
