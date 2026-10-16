import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';

import { describeError } from './problem';

describe('describeError', () => {
  it('translates business reasons', () => {
    const error = new HttpErrorResponse({ status: 409, error: { reason: 'QUOTE_CHANGED', detail: 'Refund changed' } });
    expect(describeError(error)).toContain('Os valores mudaram');
  });

  it('tells how long to wait when rate limited', () => {
    const seconds = new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '20' }) });
    const minutes = new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '450' }) });

    expect(describeError(seconds)).toBe('Muitas tentativas seguidas. Tente de novo em 20 s.');
    expect(describeError(minutes)).toBe('Muitas tentativas seguidas. Tente de novo em 8 min.');
  });

  it('shows the trace id on unexpected server errors', () => {
    const fromBody = new HttpErrorResponse({ status: 500, error: { traceId: '4bf92f3577b34da6a3ce929d0e0e4736' } });
    const fromHeader = new HttpErrorResponse({ status: 502, headers: new HttpHeaders({ 'X-Trace-Id': 'abc123' }) });

    expect(describeError(fromBody)).toBe('Algo deu errado do nosso lado. Tente de novo (código 4bf92f3577b34da6a3ce929d0e0e4736).');
    expect(describeError(fromHeader)).toContain('código abc123');
  });

  it('falls back to a generic message', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('Sem conexão');
    expect(describeError(new Error('boom'))).toContain('Sem conexão');
  });
});
