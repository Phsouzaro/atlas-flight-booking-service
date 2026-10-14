# Atlas Flight Booking Service

Assistente de reservas de voos com IA. Monolito modular em Java 21, Spring Boot, Spring Modulith e
PostgreSQL (com pgvector), com front-end em Angular previsto para a fase 5. Projeto acadêmico e de
portfólio: catálogo e pagamento são simulados.

**Princípio central:** a IA nunca altera reservas diretamente. Ela interpreta pedidos e chama
operações controladas do backend, que valida identidade, disponibilidade, regras tarifárias e
autorização. Cancelamentos e remarcações só executam após confirmação explícita do usuário, feita
por um endpoint REST fora do LLM.

## Estado atual: fase 0 (fundação)

| Item | Estado |
|---|---|
| Monorepo, Maven, Java 21, Spring Boot 3.5 | Pronto |
| Spring Modulith com teste de fronteiras | Pronto |
| PostgreSQL + pgvector via Docker Compose | Pronto |
| Migrations com Flyway | Pronto |
| Cadastro, login e JWT (módulo `identity`) | Pronto |
| Erros em `application/problem+json` | Pronto |
| Actuator (health/probes) e Swagger UI | Pronto |
| Testes (unitário, Modulith, integração com Testcontainers) | Escritos, **ainda não executados** |
| CI no GitHub Actions | Pronto |
| ADRs iniciais | Prontos |

> Os arquivos foram gerados sem acesso ao Maven Central, então o primeiro `mvn verify` na sua
> máquina é o teste de fumaça de verdade. Veja "Primeira execução" abaixo.

## Estrutura

```
atlas-flight-booking-service/
├── backend/                     # Spring Boot (monolito modular)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/java/com/flightsai/
│       │   ├── FlightsAiApplication.java
│       │   ├── identity/        # módulo: API pública = CurrentUser
│       │   │   └── internal/    #   implementação privada (controllers, service, JPA, JWT)
│       │   └── shared/          # módulo aberto: relógio e configs comuns
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-local.yml
│       │   └── db/migration/    # V1__extensions.sql, V2__users.sql
│       └── test/                # ModularityTests, JwtPropertiesTest, AuthFlowTest
├── frontend/                    # Angular (fase 5), por enquanto só um README
├── docs/
│   ├── phase-0-guide.md         # como tudo funciona (leia primeiro)
│   └── adr/                     # decisões de arquitetura
├── .github/workflows/ci.yml
├── docker-compose.yml
└── .env.example
```

## Primeira execução

Pré-requisitos: JDK 21, Maven 3.9+, Docker.

```bash
# 1. Variáveis de ambiente
cp .env.example .env            # edite POSTGRES_PASSWORD e JWT_SECRET

# 2. Build e testes (precisa do Docker rodando, por causa do Testcontainers)
cd backend
mvn verify

# 3a. Tudo no Docker
cd ..
docker compose up --build

# 3b. Ou só o banco no Docker e o backend na IDE/terminal
docker compose up -d postgres
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Com o backend no ar:

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Testando pelo Swagger UI

1. Em `POST /auth/register`, crie um usuário.
2. Em `POST /auth/login`, faça login e copie o valor de `accessToken` da resposta.
3. Clique em **Authorize** (canto superior direito), cole **só o token**, sem a palavra `Bearer`, e confirme.
4. Agora `GET /me` (rota com cadeado) responde 200. Sem o passo 3 ela responde 401, que é o esperado.

### Testando a autenticação na mão

```bash
curl -s -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"voce@example.com","password":"senha-forte-123"}'

TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"voce@example.com","password":"senha-forte-123"}' | jq -r .accessToken)

curl -s localhost:8080/me -H "Authorization: Bearer $TOKEN"
```

## Endpoints da fase 0

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/auth/register` | Pública | Cria usuário (201, 409 se o email já existe, 400 se inválido) |
| POST | `/auth/login` | Pública | Retorna JWT (401 se credenciais inválidas) |
| GET | `/me` | Bearer | Perfil do usuário autenticado |
| GET | `/actuator/health` | Pública | Saúde da aplicação |

## Roadmap

| Fase | Entregas |
|---|---|
| 0. Fundação | Esta fase |
| 1. Catálogo | Modelo, seed, busca REST |
| 2. Booking | Hold com expiração, confirmação, concorrência no último assento |
| 3. Cancelamento e remarcação | Regras tarifárias, `PendingAction` |
| 4. Assistente de IA | Spring AI, tools, memória, RAG com pgvector |
| 5. Front-end Angular | Chat, cards, confirmação de ações |
| 6. Qualidade e polimento | Evals, observabilidade, deploy |

Detalhes no documento de planejamento do projeto e em `docs/`.
