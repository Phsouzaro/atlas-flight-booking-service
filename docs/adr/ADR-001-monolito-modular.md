# ADR-001: Monolito modular em vez de microsserviços

- **Status:** aceita
- **Data:** 2026-10-14

## Contexto

O sistema tem quatro áreas de domínio (catálogo, reservas, cancelamento, remarcação) mais um
orquestrador de IA. É um projeto de portfólio mantido por uma pessoa, com um único deploy.

## Decisão

Um único deployable, dividido em módulos com fronteiras verificadas em teste (Spring Modulith).
Módulos conversam por interfaces públicas e eventos de domínio.

## Consequências

- Operação simples: um processo, um banco, transações locais onde fazem sentido.
- Fronteiras impostas por código, não por convenção; o build quebra se forem violadas.
- Extrair um módulo para serviço no futuro é possível porque as dependências já passam por
  interfaces e eventos.
- Custo: escalar módulos de forma independente não é possível. Não é requisito deste projeto.
