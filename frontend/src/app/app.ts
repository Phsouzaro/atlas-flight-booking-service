import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from './core/auth.service';
import { DEMO } from './demo/demo-mode';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  host: { '[style.--chrome-height]': "demo ? '92px' : null" },
  template: `
    @if (demo) {
      <p class="demo-banner" role="note">
        <a href="https://github.com/Phsouzaro/atlas-flight-booking-service" target="_blank" rel="noopener">GitHub</a>
        <strong>Demo</strong>
        <span class="long">· Tudo roda no seu navegador: voos, pagamento e respostas do assistente são simulados.
          <span lang="en" class="muted">Everything runs in your browser: flights, payments and assistant replies are simulated.</span></span>
        <span class="short">· dados simulados · <span lang="en" class="muted">simulated data</span></span>
      </p>
    }
    <header class="topbar">
      <a class="brand" routerLink="/">Atlas <span>reservas</span></a>
      @if (auth.email(); as email) {
        <nav aria-label="Principal">
          <a routerLink="/chat" routerLinkActive="active">Assistente</a>
          <a routerLink="/reservas" routerLinkActive="active">Minhas reservas</a>
        </nav>
        <div class="user">
          <span class="email" [title]="email">{{ email }}</span>
          <button type="button" class="link" (click)="auth.logout()">Sair</button>
        </div>
      }
    </header>
    <main>
      <router-outlet />
    </main>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 100dvh; }
    .topbar {
      display: flex; align-items: center; gap: 1.5rem; padding: 0 1.25rem; height: 56px;
      background: var(--surface); border-bottom: 1px solid var(--border);
      position: sticky; top: 0; z-index: 10;
    }
    /* altura fixa: o chat desconta --chrome-height (barra + faixa) da altura da tela */
    .demo-banner {
      margin: 0; padding: 0 1rem; height: 36px; line-height: 36px; font-size: 0.85rem; text-align: center;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      background: var(--accent-soft); color: var(--text); border-bottom: 1px solid var(--border); box-sizing: border-box;
    }
    .demo-banner .muted { color: var(--muted); }
    .demo-banner a { float: right; font-weight: 600; }
    .demo-banner .short { display: none; }
    .brand { font-weight: 700; font-size: 1.1rem; color: var(--text); text-decoration: none; }
    .brand span { color: var(--accent); font-weight: 600; }
    nav { display: flex; gap: 0.25rem; }
    nav a { padding: 0.45rem 0.75rem; border-radius: 8px; color: var(--muted); text-decoration: none; }
    nav a.active, nav a:hover { color: var(--text); background: var(--accent-soft); }
    .user { margin-left: auto; display: flex; align-items: center; gap: 0.75rem; min-width: 0; }
    .email { color: var(--muted); font-size: 0.9rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    main { flex: 1; display: flex; flex-direction: column; min-height: 0; }
    @media (max-width: 640px) {
      .topbar { gap: 0.75rem; padding: 0 0.75rem; }
      .email { display: none; }
      .brand span { display: none; }
    }
    @media (max-width: 1100px) {
      .demo-banner .long { display: none; }
      .demo-banner .short { display: inline; }
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly demo = DEMO;
}
