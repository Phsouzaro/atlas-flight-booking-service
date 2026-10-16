import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  LOCALE_ID,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localePt from '@angular/common/locales/pt';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';
import { demoInterceptors } from './demo/demo-mode';

registerLocaleData(localePt);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // na demo (GitHub Pages) um interceptor a mais responde a /api no navegador; no build normal a lista é vazia
    provideHttpClient(withInterceptors([authInterceptor, ...demoInterceptors])),
    { provide: LOCALE_ID, useValue: 'pt-BR' },
    // antes da primeira rota: com o cookie de refresh válido, a sessão volta sem pedir senha
    provideAppInitializer(() => inject(AuthService).restore()),
  ],
};
