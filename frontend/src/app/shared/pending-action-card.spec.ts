import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import type { PendingActionView } from '../api/types';
import { PendingActionCard } from './pending-action-card';

function action(overrides: Partial<PendingActionView> = {}): PendingActionView {
  return {
    id: 'a1',
    type: 'BOOKING_CANCEL',
    status: 'PENDING',
    summary: 'Cancelar a reserva Q78VHS (voo AT100). Reembolso: BRL 559.90.',
    payload: { bookingId: 'b1', refund: 559.9, currency: 'BRL' },
    result: null,
    createdAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
    decidedAt: null,
    ...overrides,
  };
}

describe('PendingActionCard', () => {
  let fixture: ComponentFixture<PendingActionCard>;
  let http: HttpTestingController;

  async function render(value: PendingActionView): Promise<HTMLElement> {
    fixture.componentRef.setInput('action', value);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function button(root: HTMLElement, label: string): HTMLButtonElement {
    const found = [...root.querySelectorAll('button')].find((b) => b.textContent?.trim().startsWith(label));
    if (!found) {
      throw new Error(`button "${label}" not found`);
    }
    return found;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PendingActionCard);
  });

  afterEach(() => http.verify());

  it('does nothing until the user confirms in the dialog', async () => {
    const root = await render(action());
    expect(root.textContent).toContain('Reembolso');
    expect(root.textContent).toContain('R$');

    button(root, 'Confirmar…').click();
    await fixture.whenStable();
    http.expectNone('/api/pending-actions/a1/confirm'); // abrir o modal não executa nada

    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);
    button(root.querySelector('app-dialog') as HTMLElement, 'Confirmar').click();

    const request = http.expectOne('/api/pending-actions/a1/confirm');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.request.body).toEqual({ cardToken: null }); // cancelamento não cobra nada
    request.flush(action({ status: 'CONFIRMED', result: { status: 'CANCELLED', code: 'Q78VHS' } }));
    await fixture.whenStable();

    expect(changed).toHaveBeenCalledWith(expect.objectContaining({ status: 'CONFIRMED' }));
  });

  it('asks for a test card when the rebooking has something to pay', async () => {
    const root = await render(action({
      type: 'BOOKING_REBOOK',
      summary: 'Remarcar a reserva Q78VHS para o voo AT121 (BUSINESS).',
      payload: { bookingId: 'b1', newFlightId: 'f2', fareClass: 'BUSINESS', amountDue: 718, currency: 'BRL' },
    }));
    expect(root.textContent).toContain('Valor a pagar');

    button(root, 'Confirmar…').click();
    await fixture.whenStable();
    expect(root.querySelector('app-card-picker select')).not.toBeNull();

    button(root.querySelector('app-dialog') as HTMLElement, 'Confirmar').click();
    const request = http.expectOne('/api/pending-actions/a1/confirm');
    expect(request.request.body).toEqual({ cardToken: 'tok_visa' });
    request.flush(action({ status: 'CONFIRMED' }));
  });

  it('shows why the confirmation was refused and keeps the action pending', async () => {
    const root = await render(action());
    button(root, 'Confirmar…').click();
    await fixture.whenStable();
    button(root.querySelector('app-dialog') as HTMLElement, 'Confirmar').click();

    http.expectOne('/api/pending-actions/a1/confirm').flush(
      { status: 409, reason: 'QUOTE_CHANGED', detail: 'Refund changed' },
      { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(root.querySelector('[role=alert]')?.textContent).toContain('Os valores mudaram');
  });

  it('hides the buttons once expired or decided', async () => {
    const expired = await render(action({ expiresAt: new Date(Date.now() - 1000).toISOString() }));
    expect(expired.textContent).toContain('Expirada');
    expect([...expired.querySelectorAll('button')].length).toBe(0);

    const done = await render(action({ status: 'CONFIRMED', result: { status: 'CANCELLED', code: 'Q78VHS' } }));
    expect(done.textContent).toContain('Reserva Q78VHS: cancelada');
  });
});
