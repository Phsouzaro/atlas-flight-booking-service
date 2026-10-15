import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';

import { API, AuthService } from './auth.service';

/**
 * Põe o Bearer em toda chamada à API, menos as de /auth (login, refresh, logout usam o cookie). Renova o
 * token antes de ele vencer; se mesmo assim vier 401, renova uma vez e repete o request. Se a renovação
 * falhar, a sessão acabou: volta ao login lembrando onde o usuário estava.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API) || request.url.startsWith(`${API}/auth/`)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const router = inject(Router);

  return from(auth.validToken()).pipe(
    switchMap((token) => next(withToken(request, token))),
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }
      return from(auth.refresh()).pipe(
        switchMap((renewed) => {
          if (!renewed) {
            auth.expire(router.url);
            return throwError(() => error);
          }
          return next(withToken(request, auth.token())); // um retry só: outro 401 vai direto para quem chamou
        }),
      );
    }),
  );
};

function withToken<T>(request: HttpRequest<T>, token: string | null): HttpRequest<T> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}
