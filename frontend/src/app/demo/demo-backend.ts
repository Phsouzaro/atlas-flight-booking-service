import { HttpErrorResponse, HttpHeaders, HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { delay, of, switchMap, throwError, timer } from 'rxjs';

import { API } from '../core/auth.service';
import { DemoServer } from './demo-server';

function sessionStorageOrNull(): Storage | null {
  try {
    return globalThis.sessionStorage ?? null;
  } catch {
    return null;
  }
}

/** Estado da demo na aba: recarregar a página mantém as reservas; fechar a aba recomeça do zero. */
const server = new DemoServer(Date.now, sessionStorageOrNull());

/**
 * Responde a toda chamada a /api no próprio navegador, sem rede. Fica depois do authInterceptor, então o
 * Bearer, a renovação de sessão e o tratamento de erro do app rodam iguais aos de produção.
 */
export const demoBackendInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API)) {
    return next(request);
  }
  const url = new URL(request.url, 'http://demo');
  const headers: Record<string, string | null> = {};
  for (const name of request.headers.keys()) {
    headers[name.toLowerCase()] = request.headers.get(name);
  }
  const response = server.handle({
    method: request.method,
    path: url.pathname.slice(API.length),
    body: request.body,
    headers,
  });
  // o assistente "pensa" um pouco, para o indicador de digitando aparecer como no app real
  const latency = url.pathname.endsWith('/messages') && request.method === 'POST' ? 900 : 120;
  const httpHeaders = new HttpHeaders(response.headers ?? {});

  if (response.status >= 400) {
    const error = new HttpErrorResponse({ status: response.status, error: response.body, headers: httpHeaders, url: request.url });
    return timer(latency).pipe(switchMap(() => throwError(() => error)));
  }
  return of(new HttpResponse({ status: response.status, body: response.body, headers: httpHeaders, url: request.url }))
    .pipe(delay(latency));
};

