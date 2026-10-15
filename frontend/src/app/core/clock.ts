import { Injectable, OnDestroy, signal } from '@angular/core';

/** "Agora" como signal, atualizado a cada segundo, para os contadores de prazo (hold, proposta). */
@Injectable({ providedIn: 'root' })
export class Clock implements OnDestroy {
  readonly now = signal(Date.now());
  private readonly timer = setInterval(() => this.now.set(Date.now()), 1000);

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }
}

/** Chave nova para o header Idempotency-Key. */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}
