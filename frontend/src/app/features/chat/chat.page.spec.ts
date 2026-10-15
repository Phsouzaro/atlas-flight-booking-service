import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import type { AssistantReply } from '../../api/types';
import { ChatPage } from './chat.page';

const flight = {
  flightId: 'f1',
  flightNumber: 'AT120',
  origin: 'GRU (São Paulo)',
  destination: 'GIG (Rio de Janeiro)',
  departureLocal: '2026-10-28T07:30:00-03:00',
  arrivalLocal: '2026-10-28T08:35:00-03:00',
  durationMinutes: 65,
  fares: [{ fareClass: 'ECONOMY_LIGHT', name: 'Econômica Light', availableSeats: 40, pricePerPassenger: 'BRL 399.90' }],
};

function reply(overrides: Partial<AssistantReply> = {}): AssistantReply {
  return {
    conversationId: 'c1',
    messageId: 'm2',
    content: 'Achei o voo AT120.',
    toolCalls: ['searchFlights'],
    pendingActions: [],
    attachments: { flights: [flight], bookings: [] },
    promptVersion: 'v1',
    usage: { promptTokens: 10, completionTokens: 5 },
    ...overrides,
  };
}

describe('ChatPage', () => {
  let fixture: ComponentFixture<ChatPage>;
  let http: HttpTestingController;
  let root: HTMLElement;

  async function settle(): Promise<void> {
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  }

  async function type(text: string): Promise<void> {
    const textarea = root.querySelector('textarea')!;
    textarea.value = text;
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    root.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await settle();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChatPage);
    root = fixture.nativeElement;
    fixture.detectChanges();
    http.expectOne('/api/assistant/conversations').flush([]);
    await settle();
  });

  afterEach(() => http.verify());

  it('starts a conversation on the first message and shows the reply with flight cards', async () => {
    await type('voos GRU para GIG dia 28?');

    http.expectOne({ method: 'POST', url: '/api/assistant/conversations' })
      .flush({ id: 'c1', createdAt: '2026-10-15T12:00:00Z', updatedAt: '2026-10-15T12:00:00Z' });
    await settle();
    const send = http.expectOne('/api/assistant/conversations/c1/messages');
    expect(send.request.body).toEqual({ content: 'voos GRU para GIG dia 28?' });
    expect(send.request.headers.get('Idempotency-Key')).toBeTruthy();
    send.flush(reply());
    await settle();
    http.expectOne('/api/assistant/conversations').flush([]);

    expect(root.textContent).toContain('Achei o voo AT120.');
    expect(root.querySelector('app-flight-card')?.textContent).toContain('07:30');
    expect(root.querySelector('textarea')!.value).toBe(''); // o campo esvazia depois do envio
  });

  it('clears the field even when Enter comes before the next render', async () => {
    const textarea = root.querySelector('textarea')!;
    textarea.value = 'minhas reservas';
    textarea.dispatchEvent(new Event('input'));
    // sem whenStable no meio: o Enter chega antes de o Angular renderizar o que foi digitado
    textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await settle();

    expect(textarea.value).toBe('');
    http.expectOne({ method: 'POST', url: '/api/assistant/conversations' })
      .flush({ id: 'c1', createdAt: '2026-10-15T12:00:00Z', updatedAt: '2026-10-15T12:00:00Z' });
    await settle();
    http.expectOne('/api/assistant/conversations/c1/messages').flush(reply({ attachments: { flights: [], bookings: [] } }));
    await settle();
    http.expectOne('/api/assistant/conversations').flush([]);
  });

  it('choosing a fare only fills the message, it never books', async () => {
    await type('voos?');
    http.expectOne({ method: 'POST', url: '/api/assistant/conversations' })
      .flush({ id: 'c1', createdAt: '2026-10-15T12:00:00Z', updatedAt: '2026-10-15T12:00:00Z' });
    await settle();
    http.expectOne('/api/assistant/conversations/c1/messages').flush(reply());
    await settle();
    http.expectOne('/api/assistant/conversations').flush([]);

    root.querySelector<HTMLButtonElement>('app-flight-card button')!.click();
    await settle();

    expect(root.querySelector('textarea')!.value).toContain('Quero o voo AT120 na tarifa Econômica Light');
    http.expectNone(() => true);
  });

  it('retries a failed message with the same Idempotency-Key', async () => {
    await type('oi');
    http.expectOne({ method: 'POST', url: '/api/assistant/conversations' })
      .flush({ id: 'c1', createdAt: '2026-10-15T12:00:00Z', updatedAt: '2026-10-15T12:00:00Z' });
    await settle();
    const first = http.expectOne('/api/assistant/conversations/c1/messages');
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({ status: 503 }, { status: 503, statusText: 'Service Unavailable' });
    await settle();

    expect(root.textContent).toContain('indisponível');
    [...root.querySelectorAll('button')].find((b) => b.textContent?.includes('Tentar de novo'))!.click();
    await settle();

    const retry = http.expectOne('/api/assistant/conversations/c1/messages');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(reply({ content: 'Olá!', attachments: { flights: [], bookings: [] } }));
    await settle();
    http.expectOne('/api/assistant/conversations').flush([]);
    expect(root.textContent).toContain('Olá!');
  });
});
