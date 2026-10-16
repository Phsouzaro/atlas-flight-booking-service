# ADR-006: Rate limit em memória, por rota e por conta

- **Status:** aceita
- **Data:** 2026-10-15

## Contexto

Duas rotas precisam de freio. O login, contra força bruta na senha. O chat, porque cada mensagem custa
tokens da OpenAI e um cliente em loop vira conta alta. O cadastro também, para não virar fábrica de contas.

## Decisão

- **Token bucket com Bucket4j**, buckets num cache Caffeine (somem depois de 1 dia sem uso, no máximo 100
  mil chaves). O tempo vem do `Clock` da aplicação, para os testes avançarem o relógio.
- **Regras por rota no `application.yml`** (`app.rate-limit.rules`), aplicadas por um `HandlerInterceptor`
  em `shared.ratelimit`, depois do Spring Security:
  - login: 20 por minuto por IP;
  - cadastro: 5 por hora por IP;
  - chat: 10 por minuto e 200 por dia por usuário (o segundo é o teto de custo).
- **Senha errada por email** (`app.rate-limit.login-failures`, 5 a cada 15 minutos), contada no
  `AuthService`. O limite por IP não segura força bruta distribuída em muitos IPs contra uma conta; este
  segura. Só falha gasta ficha, e o bloqueio vem antes de comparar a senha, com a mesma resposta exista o
  email ou não.
- **Resposta 429** em problem+json com `reason: RATE_LIMITED`, `retryAfterSeconds` e o header `Retry-After`.
  O front mostra quanto esperar.
- **IP real atrás do nginx:** `server.forward-headers-strategy=native` (o Tomcat só aceita
  X-Forwarded-For vindo de rede interna) e o nginx, que é a borda, **sobrescreve** o X-Forwarded-For com o
  IP da conexão. Se ele só acrescentasse, o cliente mandaria um valor falso por requisição e fugiria do
  limite por IP.

## Por quê

- Bucket4j é a implementação de token bucket mais usada no ecossistema Java, e o token bucket aceita
  rajada curta (abrir o app e mandar duas mensagens) sem liberar volume sustentado.
- Em memória é suficiente para o deploy de hoje: uma instância no Compose.

## Consequências

- **Uma instância só.** Com réplicas, cada uma teria os próprios buckets e o limite efetivo multiplicaria.
  A troca é configurar o Bucket4j com o backend `bucket4j-postgresql` (ou Redis) atrás da mesma
  `RateLimiter`; nada fora de `shared.ratelimit` muda.
- Reiniciar o backend zera os contadores.
- Bloqueio por email permite que alguém trave uma conta por 15 minutos errando a senha dela de propósito.
  Aceito: é curto e não exige nada da vítima; a alternativa (só por IP) deixa a conta exposta.
- Os testes que não são de rate limit e criam muitos usuários desligam com `app.rate-limit.enabled=false`;
  o comportamento fica coberto no `RateLimitTest`.
