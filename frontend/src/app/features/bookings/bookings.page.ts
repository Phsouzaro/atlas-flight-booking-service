import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import type { BookingView, PendingActionView } from '../../api/types';
import { BookingsApi, PendingActionsApi } from '../../core/api';
import { Clock, newIdempotencyKey } from '../../core/clock';
import { InstantPipe, MoneyPipe, bookingStatusLabel, fareClassLabel, remaining } from '../../core/format';
import { describeError, problemOf } from '../../core/problem';
import { statusBadge } from '../../shared/booking-card';
import { CardPicker } from '../../shared/card-picker';
import { Dialog } from '../../shared/dialog';
import { PendingActionCard } from '../../shared/pending-action-card';

@Component({
  selector: 'app-bookings-page',
  imports: [MoneyPipe, InstantPipe, Dialog, CardPicker, PendingActionCard],
  templateUrl: './bookings.page.html',
  styleUrl: './bookings.page.scss',
})
export class BookingsPage implements OnInit {
  private readonly api = inject(BookingsApi);
  private readonly actionsApi = inject(PendingActionsApi);
  private readonly clock = inject(Clock);
  private readonly router = inject(Router);

  /** `?pagar=<id>` vindo do card do chat abre o pagamento direto. */
  readonly pagar = input<string>();

  protected readonly bookings = signal<BookingView[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  // pagamento
  protected readonly paying = signal<BookingView | null>(null);
  protected readonly payBusy = signal(false);
  protected readonly payError = signal<string | null>(null);
  protected readonly cardToken = signal('tok_visa');
  private payKey = newIdempotencyKey();

  // cancelamento: a cotação vira uma ação pendente mostrada embaixo da reserva
  protected readonly quoting = signal<string | null>(null);
  protected readonly proposals = signal<Record<string, PendingActionView>>({});

  protected readonly statusLabel = bookingStatusLabel;
  protected readonly fareLabel = fareClassLabel;
  protected readonly badge = statusBadge;
  protected readonly now = this.clock.now;
  protected readonly hasActive = computed(() => (this.bookings() ?? []).some((b) => b.status === 'HELD' || b.status === 'CONFIRMED'));

  async ngOnInit(): Promise<void> {
    await this.load();
    const id = this.pagar();
    const target = id ? this.bookings()?.find((b) => b.id === id && b.status === 'HELD') : undefined;
    if (target) {
      this.startPayment(target);
    }
  }

  protected countdown(iso: string): string | null {
    return remaining(iso, this.now());
  }

  protected startPayment(booking: BookingView): void {
    this.paying.set(booking);
    this.payError.set(null);
    this.payKey = newIdempotencyKey();
  }

  protected closePayment(): void {
    this.paying.set(null);
    void this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  protected async pay(): Promise<void> {
    const booking = this.paying();
    if (!booking) {
      return;
    }
    this.payBusy.set(true);
    this.payError.set(null);
    try {
      const paid = await firstValueFrom(this.api.pay(booking.id, this.cardToken(), this.payKey));
      this.replace(paid);
      this.notice.set(`Reserva ${paid.code} confirmada.`);
      this.closePayment();
    } catch (e) {
      this.payError.set(describeError(e));
      if (problemOf(e).status !== 0) {
        this.payKey = newIdempotencyKey(); // recusa não foi gravada: outra tentativa é nova
      }
      if (problemOf(e).reason === 'HOLD_EXPIRED') {
        void this.load();
      }
    } finally {
      this.payBusy.set(false);
    }
  }

  protected async quoteCancellation(booking: BookingView): Promise<void> {
    this.quoting.set(booking.id);
    this.error.set(null);
    try {
      const quote = await firstValueFrom(this.api.quoteCancellation(booking.id));
      const action = await firstValueFrom(this.actionsApi.get(quote.pendingActionId));
      this.proposals.update((p) => ({ ...p, [booking.id]: action }));
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.quoting.set(null);
    }
  }

  protected async proposalChanged(bookingId: string, action: PendingActionView): Promise<void> {
    this.proposals.update((p) => ({ ...p, [bookingId]: action }));
    if (action.status === 'CONFIRMED') {
      await this.load();
      this.notice.set('Reserva cancelada.');
    }
  }

  /** Remarcar precisa escolher outro voo: o assistente busca e prepara a proposta. */
  protected rebook(booking: BookingView): void {
    void this.router.navigate(['/chat'], { queryParams: { mensagem: `Quero remarcar a reserva ${booking.code}.` } });
  }

  private async load(): Promise<void> {
    try {
      this.bookings.set(await firstValueFrom(this.api.list()));
      this.error.set(null);
    } catch (e) {
      this.error.set(describeError(e));
    }
  }

  private replace(updated: BookingView): void {
    this.bookings.update((list) => (list ?? []).map((b) => (b.id === updated.id ? updated : b)));
  }
}
