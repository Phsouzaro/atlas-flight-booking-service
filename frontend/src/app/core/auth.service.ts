import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import type { TokenResponse } from '../api/types';

export const API = '/api';
const STORAGE_KEY = 'atlas.session';

interface Session {
  token: string;
  email: string;
  /** epoch em ms; um pouco antes do exp do token, para não mandar token que vence no caminho */
  expiresAt: number;
}

/**
 * Sessão do usuário. O token fica no sessionStorage: some ao fechar a aba e não vai em cookie, então não
 * há CSRF. O backend não tem refresh token ainda (fase 6); quando vence, o usuário entra de novo.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(load());

  readonly email = computed(() => this.session()?.email ?? null);

  token(): string | null {
    const session = this.session();
    if (session && session.expiresAt <= Date.now()) {
      this.clear();
      return null;
    }
    return session?.token ?? null;
  }

  isAuthenticated(): boolean {
    return this.token() !== null;
  }

  async login(email: string, password: string): Promise<void> {
    const response = await firstValueFrom(this.http.post<TokenResponse>(`${API}/auth/login`, { email, password }));
    const session: Session = {
      token: response.accessToken,
      email: email.trim().toLowerCase(),
      expiresAt: Date.now() + response.expiresIn * 1000 - 30_000,
    };
    this.session.set(session);
    save(session);
  }

  async register(email: string, password: string): Promise<void> {
    await firstValueFrom(this.http.post(`${API}/auth/register`, { email, password }));
    await this.login(email, password);
  }

  /** @param returnUrl para onde voltar depois de entrar de novo */
  logout(returnUrl?: string): void {
    this.clear();
    void this.router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  }

  private clear(): void {
    this.session.set(null);
    save(null);
  }
}

function load(): Session | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const session = raw ? (JSON.parse(raw) as Session) : null;
    return session && session.expiresAt > Date.now() ? session : null;
  } catch {
    return null;
  }
}

function save(session: Session | null): void {
  try {
    if (session) {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } else {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // storage bloqueado (modo privado restrito): a sessão vale só enquanto a página estiver aberta
  }
}
