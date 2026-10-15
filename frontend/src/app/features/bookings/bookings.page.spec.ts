import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import type { BookingView } from '../../api/types';
import { BookingsPage } from './bookings.page';

function held(): BookingView {
  return {
    id: 'b1',
    code: 'HKS5MJ',
    status: 'HELD',
    total: { amount: 399.9, currency: 'BRL' },
    createdAt: new Date().toISOString(),
    holdExpiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
    confirmedAt: null,
    expiredAt: null,
    cancelledAt: null,
    rebookedAt: null,
    paymentReference: null,
    refund: null,
    rebookedFromId: null,
    rebookedToId: null,
    items: [{
      flightId: 'f1', flightNumber: 'AT120', origin: 'GRU', destination: 'GIG',
      departureAt: '2026-10-25T10:30:00Z', fareClass: 'ECONOMY_LIGHT', seats: 1,
      unitPrice: { amount: 399.9, currency: 'BRL' },
    }],
    passengers: [{ fullName: 'Ana Teste', documentNumber: 'X1', birthDate: '1990-01-01' }],
  };
}

describe('BookingsPage', () => {
  let fixture: ComponentFixture<BookingsPage>;
  let http: HttpTestingController;
  let root: HTMLElement;

  const settle = async () => {
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };
  const button = (label: string) =>
    [...root.querySelectorAll('button')].find((b) => b.textContent?.trim() === label) as HTMLButtonElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BookingsPage);
    root = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/bookings').flush([held()]);
    await settle();
  });

  afterEach(() => http.verify());

  it('releases a hold only after confirming in the dialog', async () => {
    button('Desistir').click();
    await settle();
    http.expectNone('/api/bookings/b1/release'); // abrir o modal não faz nada

    const dialog = root.querySelector('app-dialog')!;
    [...dialog.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Desistir')!.click();
    const request = http.expectOne('/api/bookings/b1/release');
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({ ...held(), status: 'CANCELLED', cancelledAt: new Date().toISOString(), refund: { amount: 0, currency: 'BRL' } });
    await settle();

    expect(root.textContent).toContain('Assentos da reserva HKS5MJ liberados.');
    expect(root.textContent).toContain('Cancelada');
    expect(root.querySelector('app-dialog')).toBeNull();
  });
});
