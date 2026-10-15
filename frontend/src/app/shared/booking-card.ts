import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import type { BookingInfo } from '../api/types';
import { LocalDateTimePipe, MoneyPipe, bookingStatusLabel, fareClassLabel } from '../core/format';

/** Resumo de reserva devolvido pelo assistente. Pagar leva para a tela de reservas. */
@Component({
  selector: 'app-booking-card',
  imports: [RouterLink, MoneyPipe, LocalDateTimePipe],
  template: `
    @let b = booking();
    <article class="card booking">
      <header>
        <strong>Reserva {{ b.code }}</strong>
        <span class="badge" [class]="badgeClass()">{{ statusLabel() }}</span>
      </header>
      <p>{{ b.flightNumber }} · {{ b.route }} · {{ b.departureLocal | localDateTime }}</p>
      <p class="muted">{{ fare() }} · {{ b.passengers }} {{ b.passengers === 1 ? 'passageiro' : 'passageiros' }}
        ({{ b.passengerNames.join(', ') }})</p>
      <footer>
        <strong>{{ b.total | money }}</strong>
        @if (b.status === 'HELD') {
          <a class="pay" routerLink="/reservas" [queryParams]="{ pagar: b.bookingId }">Pagar</a>
        }
      </footer>
    </article>
  `,
  styles: `
    .booking { display: grid; gap: 0.35rem; }
    header, footer { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
    p { margin: 0; }
    .pay { font-weight: 600; }
  `,
})
export class BookingCard {
  readonly booking = input.required<BookingInfo>();

  protected readonly statusLabel = computed(() => bookingStatusLabel(this.booking().status));
  protected readonly fare = computed(() => fareClassLabel(this.booking().fareClass));
  protected readonly badgeClass = computed(() => statusBadge(this.booking().status));
}

export function statusBadge(status: string): string {
  switch (status) {
    case 'CONFIRMED': return 'badge ok';
    case 'HELD': return 'badge warn';
    case 'CANCELLED':
    case 'EXPIRED': return 'badge danger';
    default: return 'badge';
  }
}
