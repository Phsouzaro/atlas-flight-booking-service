# ADR-007: Sessão com access token curto e refresh token rotacionado em cookie

- **Status:** aceita
- **Data:** 2026-10-15

## Contexto

Até a fase 5 havia só um JWT de 1h, guardado no `sessionStorage`. Problemas: a sessão caía no meio do uso,
não havia como encerrar uma sessão roubada antes de vencer, e qualquer script injetado na página lia o token.

## Decisão

- **Access token (JWT HS256) de 15 minutos**, mantido só em memória no front. O decoder agora valida o
  issuer (antes não validava) e usa o `Clock` da aplicação.
- **Refresh token opaco** (32 bytes aleatórios) num cookie `HttpOnly`, `Secure`, `SameSite=Strict`. O
  JavaScript da página não o lê e outro site não consegue enviá-lo. No banco (`refresh_tokens`, V14) fica só
  o hash SHA-256.
- **Rotação a cada uso:** `POST /auth/refresh` troca o token por um novo e marca o antigo como substituído.
  Tokens do mesmo login formam uma **família**.
- **Detecção de roubo:** reapresentar um token já trocado revoga a família inteira e grava
  `REFRESH_TOKEN_REUSED` no audit. Exceção: até 10 s depois da troca é tratado como duas abas renovando juntas
  (409 `REFRESH_RACE`, o cliente tenta de novo com o cookie já atualizado).
- **Expiração:** 7 dias sem uso (anda a cada rotação) e no máximo 30 dias desde o login.
- **Revogação:** `POST /auth/logout` encerra a família do cookie atual; `DELETE /me/sessions` encerra todas
  as sessões do usuário (audit `SESSIONS_REVOKED`).
- **Front:** renova antes de o token vencer e depois de um 401, com uma renovação por vez; restaura a sessão
  pelo cookie quando o app abre.

## Consequências

- Revogar não derruba um access token já emitido: ele vale até vencer, no máximo 15 minutos. É o preço de
  não consultar o banco a cada request. Se um dia precisar de corte imediato, a saída é uma versão de sessão
  no usuário conferida no JWT (com cache).
- O cookie tem `Path=/` e vai em toda chamada à API, não só em `/auth`. Não há prejuízo (o backend só o lê
  nas rotas de sessão) e evita depender do prefixo `/api` do nginx.
- `SameSite=Strict` é o que protege `/auth/refresh` e `/auth/logout` contra CSRF; não há token anti-CSRF.
  Navegadores sem suporte a SameSite não são alvo.
- Swagger e clientes de linha de comando usam o access token do corpo do login, como antes; para renovar,
  precisam guardar o cookie.
- Tokens vencidos e revogados ficam na tabela até a limpeza periódica (item de limpeza da fase 6).
