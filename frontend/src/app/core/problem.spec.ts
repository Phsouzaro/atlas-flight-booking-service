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

  it('falls back to a generic message', () => {
    expect(describeError(new HttpErrorResponse({ status: 0 }))).toContain('Sem conexão');
    expect(describeError(new Error('boom'))).toContain('Sem conexão');
  });
});
