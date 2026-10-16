# Decisões de arquitetura

Cada ADR registra uma decisão que muda a arquitetura: o contexto, o que foi decidido, por quê e o que custa.
Decisão nova vira um arquivo novo; uma decisão substituída fica, com o status apontando para a que a substituiu.

| ADR | Decisão | Fase |
|---|---|---|
| [001](ADR-001-monolito-modular.md) | Monolito modular em vez de microsserviços | 0 |
| [002](ADR-002-ia-sem-escrita-direta.md) | A IA não tem permissão de escrita direta | 0 |
| [003](ADR-003-spring-modulith-e-flyway.md) | Spring Modulith para fronteiras e Flyway para o schema | 0 |
| [004](ADR-004-concorrencia-de-assentos.md) | Update condicional para a disputa de assentos | 2 |
| [005](ADR-005-assistente-loop-memoria-rag.md) | Desenho do assistente: loop de tools, memória, RAG e PII | 4 |
| [006](ADR-006-rate-limit.md) | Rate limit em memória, por rota e por conta | 6 |
| [007](ADR-007-sessao-refresh-token.md) | Sessão com access token curto e refresh token rotacionado | 6 |
