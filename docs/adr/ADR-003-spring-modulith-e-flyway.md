# ADR-003: Spring Modulith para fronteiras e Flyway para o schema

- **Status:** aceita
- **Data:** 2026-10-14

## Decisão

1. **Spring Modulith** impõe as fronteiras entre módulos: apenas o pacote raiz do módulo é
   público, `internal` é privado. `ModularityTests` roda no CI.
2. **Flyway** é dono do schema. O Hibernate usa `ddl-auto: validate`. Migrations são imutáveis
   depois de aplicadas.
3. **JPA moderno** como acesso a dados: Hibernate 6, records como projeções de leitura,
   Specifications para buscas dinâmicas e entity graphs contra N+1.

## Consequências

- Divergência entre entidades e schema é detectada na subida da aplicação.
- Mudanças de schema são revisáveis como código e reproduzíveis em qualquer ambiente.
- Disciplina extra: toda alteração de tabela exige uma nova migration.
