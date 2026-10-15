import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
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
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
}
