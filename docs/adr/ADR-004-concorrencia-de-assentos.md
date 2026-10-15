# ADR-004: Update condicional para a disputa de assentos

- **Status:** aceita
- **Data:** 2026-10-15

## Contexto

Na fase 2, duas pessoas podem tentar segurar o último assento de uma tarifa ao mesmo tempo. Ler o
disponível, checar na aplicação e gravar depois é a receita clássica de overbooking: as duas
transações leem 1, as duas passam e o estoque vai a -1 (ou, com a CHECK da V3, uma delas explode com
erro de constraint em vez de um 409 limpo).

As duas opções consideradas:

1. **Update condicional:** um único comando checa e decrementa.
   `UPDATE seat_inventory SET available_seats = available_seats - :n WHERE ... AND available_seats >= :n`.
   Uma linha afetada significa que reservou; zero significa que não havia assento.
2. **Lock otimista:** coluna `version` na `seat_inventory`, decremento em memória e
   `OptimisticLockException` quando outra transação gravou antes. Exige retry.

## Decisão

**Update condicional** (`SeatInventoryRepository.decrementAvailable`, exposto como
`AvailabilityApi.reserveSeats`). A devolução é o espelho: `available_seats + :n <= total_seats`.

## Por quê

- **Atômico no banco:** o Postgres trava a linha só durante o `UPDATE`. Quem chega depois espera
  milissegundos, reavalia o `WHERE` com o valor novo e recebe 0 linhas. Não há janela entre ler e gravar.
- **Sem retry:** com lock otimista, sob disputa (voo popular, último assento) a maioria das tentativas
  falha por conflito de versão mesmo quando ainda haveria assento, e cada falha volta para o retry. Aqui
  a resposta já é a final: reservou ou não tem.
- **Sem lock longo:** nada de `SELECT ... FOR UPDATE` segurando a linha do inventário enquanto o resto do
  hold (código, passageiros, auditoria) é gravado.
- **Rede de segurança:** a CHECK `available_seats BETWEEN 0 AND total_seats` da V3 garante o invariante
  mesmo que algum código futuro escreva sem passar por aqui.

## Consequências

- O decremento é um update em lote do JPA: ele não passa pelo contexto de persistência. Uma
  `SeatInventory` carregada antes na mesma transação fica com o número velho. Quem reserva não deve
  confiar nesse valor depois de chamar `reserveSeats`.
- A reserva só tira assento; o estado do hold (expiração, confirmação) é do módulo `booking`. Para a
  linha da **reserva** o caso é outro: confirmar envolve cobrar e o job de expiração disputa a mesma
  linha, então ali usamos `SELECT ... FOR UPDATE` na confirmação e `FOR UPDATE SKIP LOCKED` no job.
  Quem pegar a linha primeiro decide; o outro vê o status já mudado.
- Teste obrigatório: `BookingConcurrencyTest` dispara requisições simultâneas pelo último assento e
  confere que exatamente uma passa e o estoque termina em zero.
