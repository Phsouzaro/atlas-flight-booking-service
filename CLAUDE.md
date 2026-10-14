# atlas-flight-booking-service

Serviço de reservas de voos com um assistente de IA em cima. Projeto pessoal e de portfólio: o catálogo
de voos e o pagamento são simulados, o interesse está na arquitetura e em usar LLM sem deixar ele
mexer em nada sozinho.

Backend em Java, monolito modular. Front em Angular (ainda não começou). Tudo roda com Docker Compose.

## Stack

- Java 21, Spring Boot 3.5.6, Maven
- Spring Modulith 1.4.3 (fronteiras entre módulos checadas em teste)
- Spring Data JPA + Hibernate 6, Flyway, PostgreSQL 16 com pgvector (`pgvector/pgvector:pg16`)
- Spring Security como resource server, JWT HS256 (Nimbus), senha com `DelegatingPasswordEncoder`
- springdoc 2.8.9 (Swagger UI)
- JUnit 5 + Testcontainers (Postgres real, sem H2)
- Fase 4: Spring AI com OpenAI + RAG no pgvector. Ainda não está no `pom.xml`.
- Fase 5: Angular com standalone components e signals

## Regras que não mudam

1. **A IA nunca escreve direto.** As tools só consultam e propõem. Cancelar e remarcar viram uma
   `PendingAction`, e quem executa é um endpoint REST chamado pela UI depois que o usuário confirma.
   Não existe tool `cancelBooking`. Detalhe em `docs/adr/ADR-002-ia-sem-escrita-direta.md`.
2. **O usuário vem do token.** Quem precisa saber "quem está pedindo" usa `identity.CurrentUser`.
   Nunca como parâmetro que o cliente ou o modelo escolhe.
3. **Módulos.** Só o pacote raiz do módulo é público. O que está em `internal` é privado, e as classes lá
   são package-private. Nada de acessar repositório ou tabela de outro módulo; conversa por interface
   pública ou evento. `ModularityTests` quebra o build se isso for violado.
4. **Schema é do Flyway.** `ddl-auto: validate`. Migration aplicada não se edita, cria-se a próxima.
5. **Erros em `application/problem+json`** (RFC 9457).
6. **Dinheiro** em `NUMERIC` com código da moeda. Nunca `double`.
7. **Tempo** pelo bean `Clock` de `shared`, para dar para controlar nos testes.
8. **Teste de integração contra Postgres de verdade**, mesma imagem do Compose.

## Rodando

Windows, JDK 21, Docker Desktop aberto.

```
cp .env.example .env          # ajustar POSTGRES_PASSWORD e JWT_SECRET (mínimo 32 caracteres)

cd backend
mvn verify                    # precisa do Docker por causa do Testcontainers

docker compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=local

docker compose up --build     # sobe tudo em container (na raiz)
```

Swagger em `http://localhost:8080/swagger-ui.html`. Para testar rota protegida: `/auth/login`, copiar o
`accessToken`, **Authorize**, colar só o token (sem `Bearer`).

## Estrutura

```
backend/src/main/java/com/flightsai/
  identity/            API pública: CurrentUser
  identity/internal/   cadastro, login, JWT, segurança
  shared/              Clock, configuração do OpenAPI (módulo aberto)
backend/src/main/resources/db/migration/   V1 extensions, V2 users
docs/phase-0-guide.md  como a fundação funciona + checklist para novo módulo
docs/adr/              decisões de arquitetura
frontend/              vazio por enquanto
```

## O que já está feito

Fase 0 (fundação):

- Monorepo, Maven, Spring Boot, Modulith com teste de fronteiras
- Postgres + pgvector no Compose, com healthcheck; backend só sobe depois do banco
- Flyway: `V1__extensions.sql` (pgvector), `V2__users.sql`
- Módulo `identity`: `POST /auth/register`, `POST /auth/login`, `GET /me`
  - email normalizado em minúsculas, 409 em duplicado, 401 em credencial inválida
  - hash descartável no login para não vazar quais emails existem pelo tempo de resposta
  - token com `sub` = id do usuário e claim `role`, expira em 1h
  - sem `JWT_SECRET` válido a aplicação não sobe (de propósito)
- Swagger com esquema Bearer (botão Authorize, cadeado só nas rotas protegidas)
- Actuator: `/actuator/health` público
- Testes: `ModularityTests`, `JwtPropertiesTest`, `AuthFlowTest` (Testcontainers), `OpenApiConfigTest`
- Dockerfile em dois estágios, usuário não-root
- CI no GitHub Actions (`mvn -B verify`)
- ADR-001 (monolito modular), ADR-002 (IA sem escrita direta), ADR-003 (Modulith + Flyway)
- Projeto renomeado para `atlas-flight-booking-service`

Confirmado rodando na minha máquina: `AuthFlowTest` passando (depois do contorno do Testcontainers) e o
Swagger subindo.

## A confirmar

Coisas feitas depois da última execução completa, ainda sem ver verde:

- [ ] `OpenApiConfigTest` e `ModularityTests` depois do rename do `artifactId`
- [ ] `docker compose up --build` com o jar renomeado no Dockerfile
- [ ] `/me` respondendo 200 com o token colocado no Authorize
- [ ] Primeiro commit na `main` e CI verde no GitHub

## Pendências de nome

O projeto virou `atlas-flight-booking-service`, mas o código ainda usa o nome antigo em alguns lugares:

- [ ] Pacote `com.flightsai` e `groupId` do `pom.xml`
- [ ] Classe `FlightsAiApplication` (e a referência a ela em `ModularityTests`)
- [ ] Issuer `"flights-ai"` no `TokenService`
- [ ] Método `flightsAiOpenApi()` no `OpenApiConfig` (e no teste)

Banco, usuário e senha padrão continuam `flightsai`. É interno, só mexer se der vontade (muda o `.env` e o volume).

## O que falta

### Fase 1: catálogo

- [ ] Migration `V3` com `airports`, `flights`, `fare_classes`, `seat_inventory`
- [ ] Módulo `catalog` com API pública `FlightSearchApi` e `AvailabilityApi` (a segunda é a que o booking vai usar)
- [ ] `GET /airports`, `GET /flights?origin&destination&date&pax`, `GET /flights/{id}`, todos autenticados
- [ ] Busca com `Specification`, projeções em records, entity graph para evitar N+1
- [ ] Seeder só no perfil `local`: uns 12 aeroportos (maioria brasileiros, mais MIA, JFK, LIS) e voos para os próximos 60 dias, determinístico
- [ ] Só ida por enquanto, preço fixo por classe tarifária
- [ ] Testes: busca por data, passageiros acima da disponibilidade, origem igual ao destino, rota sem voo

### Fase 2: reservas

- [ ] Módulo `booking`: `bookings`, `booking_items`, `passengers`
- [ ] Estados `HELD -> CONFIRMED -> CANCELLED | REBOOKED`, e `EXPIRED`
- [ ] Hold de uns 15 min, com job que expira e devolve o assento
- [ ] Concorrência no último assento. Decidir entre update condicional (`WHERE available >= :n`) e lock otimista com `version`, e registrar como ADR-004. Teste de concorrência obrigatório.
- [ ] `Idempotency-Key` nos POSTs que mudam estado
- [ ] `PaymentGateway` como interface, com implementação simulada
- [ ] `audit_log` em toda escrita

### Fase 3: cancelamento e remarcação

- [ ] Regras tarifárias (reembolsável, multa por classe, janela de 24h)
- [ ] Cancelamento em duas etapas: cotação (sem efeito) e execução
- [ ] Remarcação em duas etapas: proposta com diferença tarifária e execução, em uma transação só
- [ ] `PendingAction` (tipo, usuário, payload jsonb, expiração, status) e `POST /pending-actions/{id}/confirm` e `/reject`
- [ ] Revalidar tudo na confirmação, não confiar no que foi cotado
- [ ] Evento `BookingCancelled` devolvendo inventário

### Fase 4: assistente de IA

- [ ] Spring AI (conferir qual versão casa com o Boot 3.5), `ChatClient` com system prompt versionado
- [ ] Tools: `searchFlights`, `getFlightDetails`, `getMyBookings`, `getBookingDetails`, `createBookingHold`, `quoteCancellation`, `proposeRebooking`. Nenhuma que execute cancelamento ou remarcação.
- [ ] Memória de conversa em Postgres
- [ ] RAG com pgvector para política (bagagem, cancelamento, tarifas). Preço e disponibilidade vêm só das tools.
- [ ] Guardrails: limite de tokens e de chamadas de tool por turno, máscara de PII em prompt e log
- [ ] Provedor: OpenAI. Perfil `local` com modelo gratuito só se precisar para teste.
- [ ] Evals: conjunto de conversas verificando a tool chamada, que nada destrutivo roda sem confirmação e que injection falha. Modelo mockado no CI.

### Fase 5: front-end Angular

- [ ] Projeto Angular em `frontend/` (standalone, signals)
- [ ] Telas: login, chat, lista de reservas, modal de confirmação
- [ ] Atalhos clicáveis no chat ("Buscar voos", "Minhas reservas", "Cancelar reserva", "Remarcar")
- [ ] Cards para resultado de busca e resumo de reserva
- [ ] Card de ação pendente chamando `/pending-actions/{id}/confirm` direto
- [ ] Interceptor de JWT e cliente tipado gerado do OpenAPI
- [ ] `Dockerfile` do front (build + nginx com proxy de `/api`) e descomentar o serviço no Compose
- [ ] SSE para streaming da resposta (pode ficar para depois)

### Fase 6: qualidade e acabamento

- [ ] Rate limit no login e no chat
- [ ] Refresh token e revogação (hoje é só um access token de 1h)
- [ ] Logs estruturados com correlation id, métricas e tracing (Micrometer + OpenTelemetry)
- [ ] README final com diagramas gerados pelo Modulith, GIF ou prints da demo
- [ ] ADR-004 em diante (concorrência, provedor de LLM, memória de conversa)
- [ ] Imagens publicadas pelo CI e deploy em nuvem, se for publicar

## Decisões já tomadas

- LLM: OpenAI + RAG
- Front: Angular, chat simples
- JPA moderno, sem jOOQ por enquanto
- Assistente só em português no início
- Pagamento só simulado
- Deploy com Compose, preparado para nuvem

## Armadilhas

- **Testcontainers com Docker Desktop recente:** erro "Could not find a valid Docker environment" mesmo com
  o Docker rodando (resposta 400 com campos vazios). Contorno no PowerShell:
  `Set-Content -Path "$env:USERPROFILE\.docker-java.properties" -Value "api.version=1.44"`.
  Definitivo seria subir a versão do Testcontainers no `pom.xml`.
- **401 no `/me` pelo Swagger** é o esperado sem token. Usar o Authorize.
- O `docker-compose.yml` usa o nome de projeto `atlas-flight-booking-service`, então o volume do Postgres
  é novo. Se der erro de senha, `docker compose down -v` e subir de novo.
- Versões do `pom.xml` foram escolhidas sem checar as mais recentes. Conferir ao atualizar.

## Como trabalhar aqui

- Uma branch por fase (`feat/fase-1-catalogo`), merge na `main` só com CI verde.
- Módulo novo: seguir o checklist da seção 9 de `docs/phase-0-guide.md`.
- Antes de criar tabela, ver se já existe migration. Nunca editar `V1` e `V2`.
- Decisão que muda a arquitetura vira ADR em `docs/adr/`.
