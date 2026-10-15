import { Component, ElementRef, afterNextRender, input, output, viewChild } from '@angular/core';

/**
 * Modal simples e acessível: foco vai para dentro ao abrir, Esc e clique fora fecham (a menos que
 * `locked`, usado enquanto uma chamada está em andamento).
 */
@Component({
  selector: 'app-dialog',
  template: `
    <div class="backdrop" (click)="dismiss()"></div>
    <div #panel class="panel card" role="dialog" aria-modal="true" [attr.aria-labelledby]="titleId"
         tabindex="-1" (keydown.escape)="dismiss()">
      <h2 [id]="titleId">{{ heading() }}</h2>
      <ng-content />
    </div>
  `,
  styles: `
    :host { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 1rem; }
    .backdrop { position: absolute; inset: 0; background: rgb(10 15 25 / 45%); }
    .panel { position: relative; width: min(460px, 100%); max-height: calc(100dvh - 2rem); overflow: auto;
             display: grid; gap: 0.85rem; padding: 1.4rem; outline: none; }
    h2 { margin: 0; font-size: 1.2rem; }
  `,
})
export class Dialog {
  private static nextId = 0;

  readonly heading = input.required<string>();
  readonly locked = input(false);
  readonly closed = output<void>();

  protected readonly titleId = `dialog-title-${Dialog.nextId++}`;
  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');

  constructor() {
    afterNextRender(() => this.panel().nativeElement.focus());
  }

  protected dismiss(): void {
    if (!this.locked()) {
      this.closed.emit();
    }
  }
}
