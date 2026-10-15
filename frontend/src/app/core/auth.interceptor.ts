import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { API, AuthService } from './auth.service';

/**
 * Põe o Bearer em toda chamada à API, menos login e cadastro. Se a API responder 401 (token vencido ou
 * revogado), encerra a sessão e manda para o login, lembrando onde o usuário estava.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(API) || request.url.startsWith(`${API}/auth/`)) {
    return next(request);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const authorized = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  return next(authorized).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401) {
        auth.logout(router.url);
      }
      return throwError(() => error);
    }),
  );
};
