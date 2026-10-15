import { Component, model } from '@angular/core';

/** Cartões de teste do pagamento simulado. Nenhum número de cartão real passa pela aplicação. */
export const TEST_CARDS = [
  { token: 'tok_visa', label: 'Visa de teste (aprova)' },
  { token: 'tok_mastercard', label: 'Mastercard de teste (aprova)' },
  { token: 'tok_decline', label: 'Cartão de teste recusado' },
] as const;

@Component({
  selector: 'app-card-picker',
  template: `
    <label>
      Cartão
      <select [value]="token()" (change)="token.set($any($event.target).value)">
        @for (card of cards; track card.token) {
          <option [value]="card.token">{{ card.label }}</option>
        }
      </select>
    </label>
    <p class="muted note">Pagamento simulado: escolha um cartão de teste. Nunca digite um cartão de verdade.</p>
  `,
  styles: `
    :host { display: grid; gap: 0.35rem; }
    .note { margin: 0; font-size: 0.82rem; }
  `,
})
export class CardPicker {
  readonly token = model<string>(TEST_CARDS[0].token);
  protected readonly cards = TEST_CARDS;
}
