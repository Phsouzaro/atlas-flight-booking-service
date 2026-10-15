import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import type { TokenResponse, UserResponse } from '../api/types';

export const API = '/api';

/** Renova o access token quando faltar menos que isso para vencer. */
const RENEW_BEFORE_MS = 30_000;

interface Session {
  token: string;
  email: string | null;
  /** epoch em ms em que o access token vence */
  expiresAt: number;
}

/**
 * Sessão do usuário. O access token (15 min) fica só em memória: nada de storage que um script injetado
 * possa ler. O refresh token mora num cookie HttpOnly que o JavaScript nem enxerga; ele renova o access
 * token e restaura a sessão quando a página recarrega.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(null);
  /** Uma renovação por vez: vários requests vencendo juntos esperam a mesma. */
  private refreshing: Promise<boolean> | null = null;

  readonly email = computed(() => this.session()?.email ?? null);

  /** Há sessão (o access token pode estar vencido; o refresh resolve). */
  isAuthenticated(): boolean {
    return this.session() !== null;
  }

  /** Token atual, mesmo perto de vencer. Para o retry depois de um refresh. */
  token(): string | null {
    return this.session()?.token ?? null;
  }

  /** Token com folga de validade, renovando antes se preciso. Null se não há sessão. */
  async validToken(): Promise<string | null> {
    const session = this.session();
    if (!session) {
      return null;
    }
    if (session.expiresAt - Date.now() > RENEW_BEFORE_MS) {
      return session.token;
    }
    return (await this.refresh()) ? this.token() : null;
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(this.http.post<TokenResponse>(`${API}/auth/login`, { email, password }));
    this.start(response, email.trim().toLowerCase());
  }

  async register(email: string, password: string): Promise<void> {
    await firstValueFrom(this.http.post(`${API}/auth/register`, { email, password }));
    await this.login(email, password);
  }

  /** Ao abrir o app: se o cookie de refresh ainda vale, a sessão volta sem pedir senha. */
  async restore(): Promise<void> {
    if (await this.refresh()) {
      try {
        const me = await firstValueFrom(this.http.get<UserResponse>(`${API}/me`));
        this.session.update((s) => (s ? { ...s, email: me.email } : s));
      } catch {
        // sem email a sessão funciona igual; o cabeçalho só não mostra quem está logado
      }
    }
  }

  /** Troca o cookie de refresh por um access token novo. False se a sessão acabou. */
  refresh(): Promise<boolean> {
    this.refreshing ??= this.doRefresh(true).finally(() => (this.refreshing = null));
    return this.refreshing;
  }

  /** Encerra a sessão no backend (revoga o refresh token) e vai para o login. */
  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post(`${API}/auth/logout`, null));
    } catch {
      // mesmo sem resposta do backend a sessão local acaba; o refresh token vence sozinho
    }
    this.session.set(null);
    await this.router.navigate(['/login']);
  }

  /** Sessão perdida (refresh recusado): volta ao login lembrando onde o usuário estava. */
  expire(returnUrl?: string): void {
    this.session.set(null);
    void this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  }

  private async doRefresh(retryRace: boolean): Promise<boolean> {
    try {
      const response = await firstValueFrom(this.http.post<TokenResponse>(`${API}/auth/refresh`, null));
      this.start(response, this.session()?.email ?? null);
      return true;
    } catch (e) {
      // 409: outra aba acabou de renovar e o cookie já é o novo; uma tentativa a mais resolve
      if (retryRace && e instanceof HttpErrorResponse && e.status === 409) {
        await new Promise((resolve) => setTimeout(resolve, 300));
        return this.doRefresh(false);
      }
      this.session.set(null);
      return false;
    }
  }

  private start(response: TokenResponse, email: string | null): void {
    this.session.set({ token: response.accessToken, email, expiresAt: Date.now() + response.expiresIn * 1000 });
  }
}
