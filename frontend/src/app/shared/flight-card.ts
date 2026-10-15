import { Component, input, output } from '@angular/core';

import type { FareInfo, FlightInfo } from '../api/types';
import { LocalDateTimePipe, MoneyPipe, formatDuration } from '../core/format';

export interface FareChoice {
  flight: FlightInfo;
  fare: FareInfo;
}

/** Voo devolvido pela busca do assistente. Escolher uma tarifa só prepara a mensagem; não reserva nada. */
@Component({
  selector: 'app-flight-card',
  imports: [MoneyPipe, LocalDateTimePipe],
  template: `
    @let f = flight();
    <article class="card flight">
      <header>
        <strong>{{ f.flightNumber }}</strong>
        <span class="muted">{{ duration() }}</span>
      </header>
      <div class="route">
        <div><span class="time">{{ time(f.departureLocal) }}</span><span class="muted">{{ f.origin }}</span></div>
        <span class="arrow" aria-hidden="true">→</span>
        <div><span class="time">{{ time(f.arrivalLocal) }}</span><span class="muted">{{ f.destination }}</span></div>
      </div>
      <p class="muted date">{{ f.departureLocal | localDateTime }}</p>
      <ul class="fares">
        @for (fare of f.fares; track fare.fareClass) {
          <li>
            <span>{{ fare.name }}</span>
            <span class="muted seats">{{ fare.availableSeats }} {{ fare.availableSeats === 1 ? 'assento' : 'assentos' }}</span>
            <strong>{{ fare.pricePerPassenger | money }}</strong>
            <button type="button" (click)="choose.emit({ flight: f, fare })"
                    [attr.aria-label]="'Escolher ' + fare.name + ' no voo ' + f.flightNumber">Escolher</button>
          </li>
        } @empty {
          <li class="muted">Sem tarifas disponíveis.</li>
        }
      </ul>
    </article>
  `,
  styles: `
    .flight { display: grid; gap: 0.5rem; }
    header { display: flex; justify-content: space-between; }
    .route { display: flex; align-items: center; gap: 0.75rem; }
    .route div { display: grid; }
    .time { font-size: 1.25rem; font-weight: 700; }
    .arrow { color: var(--muted); }
    .date { margin: 0; font-size: 0.85rem; }
    .fares { list-style: none; margin: 0; padding: 0; display: grid; gap: 0.35rem; }
    .fares li { display: grid; grid-template-columns: 1fr auto auto auto; align-items: center; gap: 0.6rem;
                padding-top: 0.35rem; border-top: 1px solid var(--border); }
    .seats { font-size: 0.82rem; }
    .fares button { padding: 0.3rem 0.6rem; font-size: 0.85rem; }
    @media (max-width: 480px) { .fares li { grid-template-columns: 1fr auto; } .seats { display: none; } }
  `,
})
export class FlightCard {
  readonly flight = input.required<FlightInfo>();
  readonly choose = output<FareChoice>();

  protected duration(): string {
    return formatDuration(this.flight().durationMinutes);
  }

  protected time(iso: string): string {
    return /T(\d{2}:\d{2})/.exec(iso)?.[1] ?? iso;
  }
}
