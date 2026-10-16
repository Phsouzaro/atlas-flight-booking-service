# Fase 0: como tudo funciona

Este guia explica o que a fase 0 entrega, por que cada peça existe e como estender o projeto nas
próximas fases sem quebrar as regras que ele estabelece.

## 1. Objetivo e definição de pronto

A fase 0 não tem funcionalidade de voo. Ela entrega uma base em que qualquer funcionalidade nova
nasce segura, testável e dentro de fronteiras claras.

Pronto quando:

- [ ] `mvn verify` passa na sua máquina (unitários, Modulith e integração com Testcontainers)
- [ ] `docker compose up --build` sobe Postgres e backend sem erro
- [ ] `/actuator/health` responde `UP`
- [ ] registrar, logar e chamar `/me` funciona via Swagger ou curl
- [ ] o CI roda verde no GitHub

## 2. Fluxo de uma requisição

```
Cliente
  │  POST /auth/login  (ou GET /me com Bearer token)
  ▼
Filtros do Spring Security
  │  /auth/** é público; o resto exige JWT válido
  │  o JwtDecoder valida assinatura (HS256) e expiração
  ▼
Controller (identity.internal)      ← valida o payload com Jakarta Validation
  ▼
Service                             ← regra de negócio e transação
  ▼
Repository (Spring Data JPA)  ──►  PostgreSQL
  │
  ▼
Erros viram application/problem+json (RFC 9457)
```

Em `/me`, o controller não recebe o id do usuário como parâmetro. Ele pergunta ao `CurrentUser`,
que lê o `subject` do token já validado. Esse é o padrão que as tools da IA vão seguir.

## 3. Fronteiras de módulo (Spring Modulith)

Cada pacote direto de `com.atlas` é um módulo. Regras:

| Local | Visibilidade |
|---|---|
| Pacote raiz do módulo (`identity`) | API pública: interfaces, DTOs e eventos que outros módulos podem usar |
| Subpacote `internal` | Privado: controllers, services, entidades, repositórios |
| `shared` | Módulo aberto, todos podem depender; mantenha pequeno e sem regra de negócio |

O teste `ModularityTests` quebra o build se um módulo acessar o `internal` de outro ou se houver
dependência cíclica. Ele também gera diagramas PlantUML em `backend/target/spring-modulith-docs`.

Como os módulos conversam:

1. **Chamada direta a uma interface pública** quando precisa de resposta imediata
   (ex.: `Booking` pergunta disponibilidade a `catalog`).
2. **Evento de domínio** quando é uma reação sem resposta
   (ex.: `BookingCancelled` faz o inventário devolver assentos).

Nunca acesse a tabela ou o repositório de outro módulo.

## 4. Segurança

- **Autenticação:** JWT assinado com HS256. O segredo vem de `JWT_SECRET` (mínimo 32 bytes). Sem
  ele a aplicação não sobe, de propósito.
- **Token:** `sub` = id do usuário, claim `role` = `USER` ou `ADMIN`, expira em 1 hora por padrão.
- **Senhas:** `DelegatingPasswordEncoder` (BCrypt por padrão), com hash descartável no login para
  não revelar quais emails existem pelo tempo de resposta.
- **Stateless:** sem sessão e sem CSRF, porque não há cookie de sessão.
- **Identidade nunca vem do cliente nem do modelo:** só do token, via `CurrentUser`.

Limitações conscientes da fase 0: sem refresh token, sem revogação e sem rate limit de login.
Entram na fase de qualidade.

## 5. Banco e migrations

- O schema pertence ao **Flyway**. O Hibernate roda com `ddl-auto: validate` e só confere.
- Arquivos em `src/main/resources/db/migration`, nomeados `V<número>__<descricao>.sql`.
- **Nunca edite uma migration já aplicada.** Crie a próxima.
- `V1` habilita o pgvector (usado pelo RAG na fase 4); `V2` cria `users`.
- Para recomeçar do zero em desenvolvimento: `docker compose down -v`.

## 6. Configuração e segredos

| Variável | Uso | Padrão |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão com o Postgres | `localhost:5432/flightsai` |
| `JWT_SECRET` | Segredo do JWT | nenhum (obrigatório) |

O perfil `local` define um segredo de desenvolvimento para rodar pela IDE. O `.env` fica fora do Git;
o `.env` documenta o que existe. `OPENAI_API_KEY` entra na fase 4 e nunca vai para o repositório.

## 7. Testes

| Teste | O que garante | Precisa de Docker |
|---|---|---|
| `ModularityTests` | Fronteiras entre módulos | Não |
| `JwtPropertiesTest` | Falha rápida com segredo ausente ou curto | Não |
| `AuthFlowTest` | Registro, login, `/me`, 401, 409, 400 contra Postgres real | Sim |

O teste de integração usa a mesma imagem do Compose (`pgvector/pgvector:pg16`), então diferença
entre teste e ambiente real é mínima.

## 8. Docker e CI

- **Dockerfile** em dois estágios (Maven para compilar, JRE enxuto para rodar), usuário não-root.
- **Compose:** `postgres` com healthcheck e `backend` que só sobe quando o banco está saudável.
  O serviço `frontend` está comentado até a fase 5.
- **CI:** `mvn -B verify` no GitHub Actions e upload dos diagramas do Modulith como artefato.
- **Caminho para nuvem:** as imagens são as mesmas. Basta apontar `DB_URL` para um PostgreSQL
  gerenciado com pgvector e injetar os segredos pela plataforma.

## 9. Como criar um módulo novo (checklist da fase 1)

1. Criar o pacote `com.atlas.catalog` com uma classe pública de API (interface ou record).
2. Colocar controllers, services, entidades e repositórios em `catalog.internal`, package-private.
3. Criar a migration `V3__catalog.sql` com as tabelas do módulo.
4. Expor ao resto do sistema apenas o que for necessário no pacote raiz.
5. Escrever o teste de integração do módulo e rodar `ModularityTests`.
6. Registrar decisões relevantes como ADR.

## 10. O que vem depois

- **Fase 1:** catálogo, seed de voos e busca REST.
- **Fase 2:** reservas com hold, expiração e concorrência.
- **Fase 3:** `PendingAction`, cancelamento e remarcação em duas etapas.
- **Fase 4:** Spring AI (`ChatClient`, tools, memória, RAG com pgvector).
- **Fase 5:** Angular com chat, cards e modal de confirmação.

## 11. Pontos para verificar na primeira execução

- Versões do `pom.xml`: patches conferidos na fase 6 (Spring Boot 3.5.16, Modulith 1.4.13, springdoc
  2.8.17). Ao atualizar de novo, confira os patches mais recentes da mesma linha.
- Se `spring-modulith-docs` ou algum import do teste não resolver, ajuste conforme a versão
  do Modulith. É a parte mais sensível a versão.
- O primeiro build baixa muitas dependências (e, pelo `./mvnw`, o próprio Maven); leva alguns minutos.
- **Testcontainers com Docker Desktop recente:** até a fase 5, o Testcontainers 1.21.3 falhava com
  "Could not find a valid Docker environment" e exigia `api.version=1.44` em `~/.docker-java.properties`.
  O 1.21.4, que vem com o Spring Boot 3.5.16, resolve; o arquivo não é mais necessário.
- **Swagger devolve 401 em `/me`:** é o esperado sem token. Use o botão **Authorize** (veja o README).
