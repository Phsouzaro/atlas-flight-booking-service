import { Component, computed, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import type { PendingActionView } from '../api/types';
import { PendingActionsApi } from '../core/api';
import { Clock, newIdempotencyKey } from '../core/clock';
import { actionStatusLabel, bookingStatusLabel, formatMoney, remaining } from '../core/format';
import { describeError, problemOf } from '../core/problem';
import { CardPicker } from './card-picker';
import { Dialog } from './dialog';

/**
 * Proposta do assistente (ou da tela de reservas) esperando o usuário. É aqui, e só aqui, que um
 * cancelamento ou uma remarcação acontece: o clique em Confirmar chama /pending-actions/{id}/confirm (ADR-002).
 */
@Component({
  selector: 'app-pending-action-card',
  imports: [Dialog, CardPicker],
  template: `
    @let a = action();
    <article class="card pending" [class.closed]="a.status !== 'PENDING' || expired()">
      <header>
        <span class="type">{{ typeLabel() }}</span>
        <span class="badge" [class]="badgeClass()">{{ statusLabel() }}</span>
      </header>
      <p class="summary">{{ a.summary }}</p>

      @if (amount(); as value) {
        <p class="amount"><span class="muted">{{ amountLabel() }}:</span>&nbsp;<strong>{{ value }}</strong></p>
      }

      @if (a.status === 'PENDING' && !expired()) {
        <p class="muted small">Nada acontece até você confirmar. Vale por mais {{ countdown() }}.</p>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <div class="actions">
          <button type="button" (click)="reject()" [disabled]="busy()">Rejeitar</button>
          <button type="button" class="primary" (click)="open()" [disabled]="busy()">Confirmar…</button>
        </div>
      } @else if (a.status === 'CONFIRMED') {
        <p class="ok small">{{ resultText() }}</p>
      }
    </article>

    @if (dialogOpen()) {
      <app-dialog [heading]="'Confirmar ' + typeLabel().toLowerCase()" [locked]="busy()" (closed)="dialogOpen.set(false)">
        <p>{{ a.summary }}</p>
        @if (amount(); as value) {
          <p><span class="muted">{{ amountLabel() }}:</span>&nbsp;<strong>{{ value }}</strong></p>
        }
        @if (needsCard()) {
          <app-card-picker [(token)]="cardToken" />
        }
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <div class="actions">
          <button type="button" (click)="dialogOpen.set(false)" [disabled]="busy()">Voltar</button>
          <button type="button" class="primary" (click)="confirm()" [disabled]="busy()">
            {{ busy() ? 'Confirmando…' : 'Confirmar' }}
          </button>
        </div>
      </app-dialog>
    }
  `,
  styles: `
    .pending { display: grid; gap: 0.5rem; border-left: 4px solid var(--accent); }
    .pending.closed { border-left-color: var(--border); }
    header { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
    .type { font-weight: 700; }
    p { margin: 0; }
    .amount strong { font-size: 1.1rem; }
    .small { font-size: 0.86rem; }
    .ok { color: var(--ok); }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; flex-wrap: wrap; }
  `,
})
export class PendingActionCard {
  private readonly api = inject(PendingActionsApi);
  private readonly clock = inject(Clock);

  readonly action = input.required<PendingActionView>();
  /** A ação depois de confirmada ou rejeitada, para a tela atualizar o resto (ex.: lista de reservas). */
  readonly changed = output<PendingActionView>();

  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dialogOpen = signal(false);
  protected readonly cardToken = signal('tok_visa');

  /** Mesma chave para todas as tentativas desta confirmação: clique duplo ou retry não executa duas vezes. */
  private confirmKey = newIdempotencyKey();

  protected readonly countdown = computed(() => remaining(this.action().expiresAt, this.clock.now()));
  protected readonly expired = computed(() => this.action().status === 'PENDING' && this.countdown() === null);

  protected readonly typeLabel = computed(() =>
    this.action().type === 'BOOKING_REBOOK' ? 'Remarcação' : this.action().type === 'BOOKING_CANCEL' ? 'Cancelamento' : 'Ação');

  protected readonly statusLabel = computed(() => (this.expired() ? 'Expirada' : actionStatusLabel(this.action().status)));

  protected readonly badgeClass = computed(() => {
    if (this.expired()) {
      return 'badge';
    }
    switch (this.action().status) {
      case 'PENDING': return 'badge info';
      case 'CONFIRMED': return 'badge ok';
      case 'REJECTED': return 'badge danger';
      default: return 'badge';
    }
  });

  /** Valor que importa para decidir: reembolso no cancelamento, valor a pagar na remarcação. */
  protected readonly amount = computed(() => {
    const payload = this.action().payload;
    const value = payload['refund'] ?? payload['amountDue'];
    const currency = payload['currency'];
    return typeof value === 'number' && typeof currency === 'string' ? formatMoney({ amount: value, currency }) : null;
  });

  protected readonly amountLabel = computed(() =>
    this.action().type === 'BOOKING_REBOOK' ? 'Valor a pagar' : 'Reembolso');

  protected readonly needsCard = computed(() => {
    const due = this.action().payload['amountDue'];
    return typeof due === 'number' && due > 0;
  });

  protected readonly resultText = computed(() => {
    const result = this.action().result;
    const status = typeof result?.['status'] === 'string' ? bookingStatusLabel(result['status']) : null;
    const code = typeof result?.['code'] === 'string' ? result['code'] : null;
    return status && code ? `Feito. Reserva ${code}: ${status.toLowerCase()}.` : 'Feito.';
  });

  protected open(): void {
    this.error.set(null);
    this.dialogOpen.set(true);
  }

  protected async confirm(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      const updated = await firstValueFrom(
        this.api.confirm(this.action().id, this.needsCard() ? this.cardToken() : null, this.confirmKey));
      this.dialogOpen.set(false);
      this.changed.emit(updated);
    } catch (e) {
      this.error.set(describeError(e));
      // erro HTTP: o backend respondeu e não gravou nada, então a próxima tentativa é nova.
      // Sem resposta (rede), pode ter executado: reusar a chave faz o retry devolver o mesmo resultado.
      if (problemOf(e).status !== 0) {
        this.confirmKey = newIdempotencyKey();
      }
    } finally {
      this.busy.set(false);
    }
  }

  protected async reject(): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.changed.emit(await firstValueFrom(this.api.reject(this.action().id, newIdempotencyKey())));
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.busy.set(false);
    }
  }
}
