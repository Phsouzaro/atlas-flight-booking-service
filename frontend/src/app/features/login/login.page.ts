import { Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { describeError, problemOf } from '../../core/problem';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  template: `
    <section class="card login">
      <h1>{{ mode() === 'login' ? 'Entrar' : 'Criar conta' }}</h1>
      <p class="muted">Assistente de reservas da Atlas. Catálogo e pagamento são simulados.</p>

      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <label>
          Email
          <input type="email" formControlName="email" autocomplete="email" required />
        </label>
        <label>
          Senha
          <input type="password" formControlName="password" required
                 [attr.autocomplete]="mode() === 'login' ? 'current-password' : 'new-password'" />
        </label>
        @if (mode() === 'register') {
          <p class="muted hint">Mínimo de 8 caracteres.</p>
        }
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <button type="submit" class="primary" [disabled]="form.invalid || busy()">
          {{ busy() ? 'Aguarde…' : mode() === 'login' ? 'Entrar' : 'Criar conta e entrar' }}
        </button>
      </form>

      <button type="button" class="link switch" (click)="toggle()">
        {{ mode() === 'login' ? 'Não tem conta? Criar conta' : 'Já tem conta? Entrar' }}
      </button>
    </section>
  `,
  styles: `
    :host { display: grid; place-items: center; padding: 2rem 1rem; flex: 1; }
    .login { width: min(400px, 100%); display: grid; gap: 0.75rem; padding: 1.75rem; }
    h1 { margin: 0; font-size: 1.5rem; }
    p { margin: 0; }
    form { display: grid; gap: 0.9rem; margin-top: 0.5rem; }
    .hint { font-size: 0.85rem; margin-top: -0.5rem; }
    .switch { justify-self: center; }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Vem da query string quando o guard ou o interceptor mandaram para cá. */
  readonly returnUrl = input<string>();

  protected readonly mode = signal<'login' | 'register'>('login');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected toggle(): void {
    this.mode.update((m) => (m === 'login' ? 'register' : 'login'));
    this.error.set(null);
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid || this.busy()) {
      return;
    }
    const { email, password } = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    try {
      if (this.mode() === 'login') {
        await this.auth.login(email, password);
      } else {
        await this.auth.register(email, password);
      }
      await this.router.navigateByUrl(this.safeReturnUrl());
    } catch (e) {
      this.error.set(this.messageFor(e));
    } finally {
      this.busy.set(false);
    }
  }

  private messageFor(e: unknown): string {
    const { status } = problemOf(e);
    if (status === 401) {
      return 'Email ou senha incorretos.';
    }
    if (status === 409) {
      return 'Esse email já tem conta. Entre com ele.';
    }
    if (status === 400) {
      return 'Confira o email e a senha (mínimo de 8 caracteres).';
    }
    return describeError(e);
  }

  /** Só caminhos internos: um returnUrl externo viraria redirecionamento aberto. */
  private safeReturnUrl(): string {
    const url = this.returnUrl();
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : '/chat';
  }
}
