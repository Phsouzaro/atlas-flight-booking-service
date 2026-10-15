import { Component, ElementRef, OnInit, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom, forkJoin, of } from 'rxjs';

import type { Attachments, AssistantReply, ConversationView, MessageView, PendingActionView } from '../../api/types';
import { AssistantApi, PendingActionsApi } from '../../core/api';
import { newIdempotencyKey } from '../../core/clock';
import { InstantPipe } from '../../core/format';
import { describeError } from '../../core/problem';
import { BookingCard } from '../../shared/booking-card';
import { FareChoice, FlightCard } from '../../shared/flight-card';
import { PendingActionCard } from '../../shared/pending-action-card';

interface ChatItem {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  attachments: Attachments;
  pendingActions: PendingActionView[];
  /** só em mensagem do usuário que falhou: a chave para reenviar sem duplicar */
  failed?: { error: string; key: string };
}

const EMPTY: Attachments = { flights: [], bookings: [] };

/** Atalhos: os que terminam em espaço só preenchem o campo, para o usuário completar. */
const SHORTCUTS = [
  { label: 'Buscar voos', text: 'Quero buscar voos de ' },
  { label: 'Minhas reservas', text: 'Quais são as minhas reservas?' },
  { label: 'Cancelar reserva', text: 'Quero cancelar uma reserva.' },
  { label: 'Remarcar', text: 'Quero remarcar uma reserva.' },
  { label: 'Bagagem', text: 'Quais são as regras de bagagem?' },
];

@Component({
  selector: 'app-chat-page',
  imports: [InstantPipe, FlightCard, BookingCard, PendingActionCard],
  templateUrl: './chat.page.html',
  styleUrl: './chat.page.scss',
})
export class ChatPage implements OnInit {
  private readonly assistant = inject(AssistantApi);
  private readonly actions = inject(PendingActionsApi);
  private readonly route = inject(ActivatedRoute);

  protected readonly shortcuts = SHORTCUTS;
  protected readonly conversations = signal<ConversationView[]>([]);
  protected readonly activeId = signal<string | null>(null);
  protected readonly items = signal<ChatItem[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly showHistory = signal(false);

  private readonly thread = viewChild<ElementRef<HTMLElement>>('thread');
  private readonly input = viewChild<ElementRef<HTMLTextAreaElement>>('input');

  constructor() {
    // desce para a última mensagem sempre que a conversa muda ou chega resposta
    effect(() => {
      this.items();
      this.sending();
      const el = this.thread()?.nativeElement;
      if (el) {
        queueMicrotask(() => (el.scrollTop = el.scrollHeight));
      }
    });
  }

  async ngOnInit(): Promise<void> {
    const prefill = this.route.snapshot.queryParamMap.get('mensagem');
    if (prefill) {
      this.draft.set(prefill);
    }
    try {
      const list = await firstValueFrom(this.assistant.conversations());
      this.conversations.set(list);
      if (list.length > 0 && !prefill) {
        await this.open(list[0].id);
      }
    } catch (e) {
      this.loadError.set(describeError(e));
    }
    this.focusInput();
  }

  protected async open(id: string): Promise<void> {
    this.activeId.set(id);
    this.showHistory.set(false);
    this.loadError.set(null);
    try {
      const messages = await firstValueFrom(this.assistant.messages(id));
      this.items.set(await this.toItems(messages));
    } catch (e) {
      this.loadError.set(describeError(e));
    }
  }

  protected newConversation(): void {
    this.activeId.set(null);
    this.items.set([]);
    this.showHistory.set(false);
    this.focusInput();
  }

  protected useShortcut(text: string): void {
    if (text.endsWith(' ')) {
      this.draft.set(text);
      this.focusInput();
    } else {
      void this.send(text);
    }
  }

  protected chooseFare({ flight, fare }: FareChoice): void {
    this.draft.set(`Quero o voo ${flight.flightNumber} na tarifa ${fare.name}. Passageiros: `);
    this.focusInput();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void this.send(this.draft());
    }
  }

  protected async send(text: string, retryKey?: string, retryId?: string): Promise<void> {
    const content = text.trim();
    if (!content || this.sending()) {
      return;
    }
    const key = retryKey ?? newIdempotencyKey();
    const id = retryId ?? key;
    if (retryId) {
      this.updateItem(retryId, { failed: undefined });
    } else {
      this.items.update((list) => [...list, { id, role: 'USER', content, attachments: EMPTY, pendingActions: [] }]);
      this.clearInput();
    }
    this.sending.set(true);
    try {
      const conversationId = this.activeId() ?? (await this.startConversation());
      const reply = await firstValueFrom(this.assistant.send(conversationId, content, key));
      this.items.update((list) => [...list, this.fromReply(reply)]);
      void this.refreshConversations();
    } catch (e) {
      this.updateItem(id, { failed: { error: describeError(e), key } });
    } finally {
      this.sending.set(false);
      this.focusInput();
    }
  }

  protected retry(item: ChatItem): void {
    if (item.failed) {
      void this.send(item.content, item.failed.key, item.id);
    }
  }

  /** O card avisou que a ação foi confirmada ou rejeitada: troca pela versão nova. */
  protected actionChanged(updated: PendingActionView): void {
    this.items.update((list) => list.map((item) => ({
      ...item,
      pendingActions: item.pendingActions.map((a) => (a.id === updated.id ? updated : a)),
    })));
  }

  private async startConversation(): Promise<string> {
    const conversation = await firstValueFrom(this.assistant.start());
    this.activeId.set(conversation.id);
    this.conversations.update((list) => [conversation, ...list]);
    return conversation.id;
  }

  private async refreshConversations(): Promise<void> {
    try {
      this.conversations.set(await firstValueFrom(this.assistant.conversations()));
    } catch {
      // lista lateral é secundária: a conversa continua funcionando
    }
  }

  private fromReply(reply: AssistantReply): ChatItem {
    return {
      id: reply.messageId,
      role: 'ASSISTANT',
      content: reply.content,
      attachments: reply.attachments,
      pendingActions: reply.pendingActions as PendingActionView[],
    };
  }

  /** Histórico traz só os ids das propostas; o estado atual de cada uma vem da API. */
  private async toItems(messages: MessageView[]): Promise<ChatItem[]> {
    const ids = [...new Set(messages.flatMap((m) => m.pendingActionIds))];
    const views = ids.length
      ? await firstValueFrom(forkJoin(ids.map((id) => this.actions.get(id))))
      : await firstValueFrom(of([] as PendingActionView[]));
    const byId = new Map(views.map((v) => [v.id, v]));
    return messages.map((m) => ({
      id: m.id,
      role: m.role === 'USER' ? 'USER' : 'ASSISTANT',
      content: m.content,
      attachments: m.attachments ?? EMPTY,
      pendingActions: m.pendingActionIds.map((id) => byId.get(id)).filter((v): v is PendingActionView => !!v),
    }));
  }

  private updateItem(id: string, patch: Partial<ChatItem>): void {
    this.items.update((list) => list.map((item) => (item.id === id ? { ...item, ...patch } : item)));
  }

  /**
   * Limpa o signal e o elemento. Só o signal não basta: se o Enter chega antes de o Angular renderizar o que
   * foi digitado, o último valor aplicado ao [value] ainda é '' e voltar o signal para '' não muda nada.
   */
  private clearInput(): void {
    this.draft.set('');
    const el = this.input()?.nativeElement;
    if (el) {
      el.value = '';
    }
  }

  private focusInput(): void {
    queueMicrotask(() => this.input()?.nativeElement.focus());
  }
}
