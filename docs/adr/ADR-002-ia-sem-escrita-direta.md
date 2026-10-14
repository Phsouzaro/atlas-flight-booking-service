# ADR-002: A IA não tem permissão de escrita direta

- **Status:** aceita
- **Data:** 2026-10-14

## Contexto

Um LLM pode ser induzido, por prompt injection ou por erro, a executar ações que o usuário não
queria. Cancelamento e remarcação envolvem dinheiro e não são triviais de desfazer.

## Decisão

- As tools da IA só **consultam** e **propõem**. Não existe tool de cancelar ou confirmar.
- Uma proposta cria uma `PendingAction` (tipo, usuário, payload, expiração, status).
- A execução acontece em um endpoint REST dedicado (`POST /pending-actions/{id}/confirm`),
  autenticado e chamado pela interface, nunca pelo modelo.
- O backend revalida tudo no momento da confirmação, dentro de uma transação.
- O id do usuário vem do token (`CurrentUser`), nunca de parâmetro escolhido pelo modelo.

## Consequências

- Elimina a classe de risco "o modelo foi convencido a confirmar sozinho".
- A interface precisa de um passo de confirmação explícito (card com Confirmar e Cancelar).
- Há um estado intermediário (`PendingAction`) a modelar, expirar e auditar.
