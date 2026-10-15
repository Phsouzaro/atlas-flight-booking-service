# ADR-005: Desenho do assistente (loop de tools, memória, RAG e PII)

- **Status:** aceita
- **Data:** 2026-10-15

## Contexto

A fase 4 põe um LLM (OpenAI, `gpt-4.1-mini` por padrão) na frente das APIs de catálogo e reservas. O
ADR-002 já define que a IA só consulta e propõe. Faltava decidir como o backend conduz o modelo, onde
guarda a conversa, como entra a base de políticas e o que fazer com dados pessoais. Spring AI 1.1.8 (a
linha compatível com Spring Boot 3.5).

## Decisões

1. **O loop de tools é do backend.** Usamos `ChatModel` + `ToolCallingManager` com
   `internalToolExecutionEnabled=false`, em vez de deixar o `ChatClient` executar as tools sozinho. Cada
   rodada passa por `AssistantService`, que:
   - bloqueia, antes de executar, qualquer tool fora da lista (`AssistantTools.NAMES`);
   - limita chamadas ao modelo (4) e tools (6) por turno;
   - soma tokens e grava quais tools rodaram;
   - coleta as `PendingAction` criadas no turno para a UI mostrar o card de confirmação.
2. **Memória própria em Postgres** (`assistant_conversations`, `assistant_messages`, V10), não o
   repositório JDBC do Spring AI. Motivos: o schema continua do Flyway; toda leitura filtra pelo dono da
   conversa; cada resposta guarda versão do prompt, tools e tokens. Vão para o prompt as últimas 20
   mensagens (usuário e assistente; resultados de tool não são guardados).
3. **RAG como tool** (`searchPolicies`), não como advisor que busca em toda mensagem. O modelo consulta a
   política quando precisa, e preço e disponibilidade nunca saem do vetor: só de `searchFlights`. Os
   documentos (`assistant/policies/*.md`) são quebrados por seção e reindexados na subida quando o hash
   muda. Tabela `vector_store` criada pela V11, no formato do `PgVectorStore`, com a criação automática
   desligada.
4. **PII tokenizada, não só mascarada.** `PiiDetector` acha cartão (Luhn), CPF, passaporte, email e
   telefone; `PiiVault` troca por `[DOC_1]`, `[EMAIL_1]`, `[TEL_1]` antes de o texto ir para o modelo ou
   para a memória, e guarda o valor real por conversa (`assistant_pii_tokens`). Quando o modelo chama
   `createBookingHold` com `[DOC_1]`, o backend troca pelo valor real. Cartão vira `[CARTAO_REMOVIDO]`
   e não é guardado. As tools devolvem passageiros só com primeiro nome e inicial, sem documento.
5. **System prompt versionado** em `assistant/prompts/system-v1.md`; a versão vai em cada resposta.
   Mudar comportamento é criar `system-v2.md` e trocar `app.assistant.prompt-version`.
6. **Idempotency-Key do chat tratada pelo próprio módulo.** O `IdempotentExecutor` roda tudo numa
   transação, o que seguraria uma conexão durante segundos de chamada ao modelo e misturaria a
   transação do turno com a das tools. Aqui a chave fica na mensagem do usuário (única por conversa);
   repetir devolve a resposta gravada. Se o modelo falhar, a mensagem é apagada e a mesma chave pode
   ser reenviada.
7. **Evals em dois níveis.** No CI, `AssistantFlowTest` usa um servidor HTTP que imita a API da OpenAI
   (`FakeOpenAi`): o Spring AI real executa as tools contra o banco real e só a escolha do modelo é
   roteirizada. Isso prova o que não depende do modelo: lista de tools, nada destrutivo sem confirmação,
   injection bloqueada, PII fora do prompt e limites. `AssistantLiveEvalTest` roda os casos de
   `assistant/evals/cases.json` contra o modelo real, só com `-Dllm.evals=true`, para medir a escolha de
   tool.

## Consequências

- Sem `OPENAI_API_KEY` a aplicação sobe normalmente; o chat responde 503 e a base de políticas não é
  indexada.
- Nome de pessoa não é detectado e vai para o modelo. Aceito: é necessário para o hold, e o modelo não
  recebe documento nem data de nascimento devolvidos pelas tools.
- `createBookingHold` é a única tool que escreve, e só segura assento por 15 minutos, sem cobrança. Pagar,
  cancelar e remarcar continuam exclusivos da UI.
- Trocar de provedor é trocar o starter e o `OpenAiChatOptions` do `AssistantService`; o resto usa as
  interfaces do Spring AI.
