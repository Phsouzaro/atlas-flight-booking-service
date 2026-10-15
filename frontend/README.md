# Front-end (Angular 22)

Chat com o assistente, lista de reservas e confirmação das ações que o assistente propõe. Standalone
components, signals, zoneless, sem biblioteca de UI.

## Rodando

Precisa de Node 24 (o Angular 22 exige 22.22+ ou 24.15+).

```bash
npm install
npm start            # http://localhost:4200, com proxy de /api para o backend em localhost:8080
npm run test:ci      # testes (Vitest)
npm run build        # build de produção em dist/atlas-web/browser
```

Pelo Compose (na raiz) o front sobe em `http://localhost:4200`, servido por nginx com o mesmo proxy de `/api`.

## Cliente da API

Os tipos vêm do contrato do backend, não são escritos à mão:

```bash
npm run api:update     # baixa /v3/api-docs do backend rodando para openapi.json (versionado)
npm run api:generate   # gera src/app/api/schema.ts a partir dele
```

`src/app/api/types.ts` só dá nomes curtos aos tipos gerados e corrige o que o OpenAPI não expressa
(`refund` nulo, `payload` livre). Os serviços em `src/app/core/api.ts` usam esses tipos com o `HttpClient`.

## Regras que a UI segue

- **Só a UI confirma.** Cancelamento e remarcação chegam como ação pendente (card com Confirmar e Rejeitar);
  a execução é `POST /pending-actions/{id}/confirm`, chamado por clique do usuário num modal (ADR-002).
- **Idempotency-Key** em todo POST que muda estado. A mesma chave é reusada no "tentar de novo" e quando a
  rede falha sem resposta; uma recusa do backend gera chave nova.
- **Pagamento simulado:** a UI só oferece cartões de teste (`tok_visa`, `tok_decline`...). Nenhum número de
  cartão passa pela aplicação.
- **Horários:** voo no fuso do aeroporto, como o backend manda; instantes (criação, prazos) no fuso de Brasília.
- **Token** no `sessionStorage`, enviado só para `/api`. 401 encerra a sessão e volta para o login.

## Estrutura

```
src/app/
  api/         schema.ts (gerado) e types.ts
  core/        auth (serviço, interceptor, guard), clientes da API, erros problem+json, formatação
  shared/      modal, card de ação pendente, cards de voo e reserva, seletor de cartão de teste
  features/    login, chat, reservas
nginx/         configuração do container (proxy, cache, CSP)
```
