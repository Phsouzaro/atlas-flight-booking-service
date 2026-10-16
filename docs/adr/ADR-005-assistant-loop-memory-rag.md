# ADR-005: Assistant design (tool loop, memory, RAG and PII)

- **Status:** accepted
- **Date:** 2026-10-15

## Context

Phase 4 puts an LLM (OpenAI, `gpt-4.1-mini` by default) in front of the catalog and booking APIs. ADR-002
already states that the AI only queries and proposes. What was left to decide: how the backend drives the
model, where the conversation is stored, how the policy documents come in and what to do with personal
data. Spring AI 1.1.8 (the line compatible with Spring Boot 3.5).

## Decisions

1. **The tool loop belongs to the backend.** We use `ChatModel` + `ToolCallingManager` with
   `internalToolExecutionEnabled=false`, instead of letting `ChatClient` run the tools by itself. Every round
   goes through `AssistantService`, which:
   - blocks, before execution, any tool that is not on the list (`AssistantTools.NAMES`);
   - limits model calls (4) and tool calls (6) per turn;
   - adds up tokens and records which tools ran;
   - collects the `PendingAction`s created during the turn so the UI can show the confirmation card.
2. **Our own memory in Postgres** (`assistant_conversations`, `assistant_messages`, V10), not Spring AI's
   JDBC repository. Reasons: the schema stays with Flyway; every read filters by the conversation's owner;
   each reply stores the prompt version, tools and tokens. The prompt gets the last 20 messages (user and
   assistant). Tool results from the 2 most recent replies also go in, as a system note (`tool_context`,
   V12): without them the model lost the `flightId` on the next turn.
3. **RAG as a tool** (`searchPolicies`), not as an advisor that searches on every message. The model looks
   up a policy when it needs one, and price and availability never come from the vector store: only from
   `searchFlights`. The documents (`assistant/policies/*.md`) are split by section and re-indexed on startup
   when their hash changes. The `vector_store` table is created by V11, in `PgVectorStore`'s format, with
   automatic schema creation turned off.
4. **PII is tokenized, not just masked.** `PiiDetector` finds card numbers (Luhn), CPF, passport, email and
   phone numbers; `PiiVault` replaces them with `[DOC_1]`, `[EMAIL_1]`, `[TEL_1]` before the text reaches the
   model or the memory, and keeps the real value per conversation (`assistant_pii_tokens`). When the model
   calls `createBookingHold` with `[DOC_1]`, the backend swaps in the real value. Card numbers become
   `[CARTAO_REMOVIDO]` and are not stored. Tools return passengers with first name and initial only, without
   documents.
5. **Versioned system prompt** in `assistant/prompts/system-v1.md`; the version goes out with every reply.
   Changing behavior means creating `system-v2.md` and switching `app.assistant.prompt-version`.
6. **The chat's Idempotency-Key is handled by the module itself.** `IdempotentExecutor` runs everything in
   one transaction, which would hold a connection for seconds during the model call and mix the turn's
   transaction with the tools' transactions. Here the key lives on the user's message (unique per
   conversation); repeating it returns the stored reply. If the model fails, the message is deleted and the
   same key can be sent again.
7. **Evals at two levels.** In CI, `AssistantFlowTest` uses an HTTP server that imitates the OpenAI API
   (`FakeOpenAi`): the real Spring AI runs the tools against the real database and only the model's choice
   is scripted. This proves what does not depend on the model: the tool list, nothing destructive without
   confirmation, injection blocked, PII kept out of the prompt, and the limits. `AssistantLiveEvalTest` runs
   the cases in `assistant/evals/cases.json` against the real model, only with `-Dllm.evals=true`, to measure
   tool choice.

## Consequences

- Without `OPENAI_API_KEY` the application starts normally; the chat answers 503 and the policy documents
  are not indexed.
- Person names are not detected and do reach the model. Accepted: they are needed for the hold, and the
  model never receives documents or birth dates back from the tools.
- `createBookingHold` is the only tool that writes, and it only holds seats for 15 minutes, without any
  charge. Paying, cancelling and rebooking remain exclusive to the UI.
- Switching providers means switching the starter and the `OpenAiChatOptions` in `AssistantService`; the rest
  uses Spring AI's interfaces.
