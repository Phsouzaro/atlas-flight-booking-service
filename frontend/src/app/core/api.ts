import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import type {
  AssistantReply,
  BookingView,
  CancellationQuote,
  ConversationView,
  MessageView,
  PendingActionView,
} from '../api/types';
import { API } from './auth.service';

const IDEMPOTENCY = 'Idempotency-Key';

function idempotent(key: string): { headers: HttpHeaders } {
  return { headers: new HttpHeaders({ [IDEMPOTENCY]: key }) };
}

/** Chat com o assistente. Só conversa: ele não paga, não cancela e não remarca. */
@Injectable({ providedIn: 'root' })
export class AssistantApi {
  private readonly http = inject(HttpClient);

  conversations(): Observable<ConversationView[]> {
    return this.http.get<ConversationView[]>(`${API}/assistant/conversations`);
  }

  start(): Observable<ConversationView> {
    return this.http.post<ConversationView>(`${API}/assistant/conversations`, null);
  }

  messages(conversationId: string): Observable<MessageView[]> {
    return this.http.get<MessageView[]>(`${API}/assistant/conversations/${conversationId}/messages`);
  }

  /** Repetir com a mesma `key` devolve a mesma resposta sem chamar o modelo de novo. */
  send(conversationId: string, content: string, key: string): Observable<AssistantReply> {
    return this.http.post<AssistantReply>(`${API}/assistant/conversations/${conversationId}/messages`,
      { content }, idempotent(key));
  }
}

@Injectable({ providedIn: 'root' })
export class BookingsApi {
  private readonly http = inject(HttpClient);

  list(): Observable<BookingView[]> {
    return this.http.get<BookingView[]>(`${API}/bookings`);
  }

  /** Paga e confirma um hold. `cardToken` é simulado: tok_decline recusa, outros aprovam. */
  pay(bookingId: string, cardToken: string, key: string): Observable<BookingView> {
    return this.http.post<BookingView>(`${API}/bookings/${bookingId}/confirm`, { cardToken }, idempotent(key));
  }

  /** Desiste de um hold não pago; os assentos voltam na hora. */
  release(bookingId: string, key: string): Observable<BookingView> {
    return this.http.post<BookingView>(`${API}/bookings/${bookingId}/release`, null, idempotent(key));
  }

  /** Só cota: cria uma ação pendente que o usuário confirma em seguida. */
  quoteCancellation(bookingId: string): Observable<CancellationQuote> {
    return this.http.post<CancellationQuote>(`${API}/bookings/${bookingId}/cancellation-quote`, null);
  }
}

@Injectable({ providedIn: 'root' })
export class PendingActionsApi {
  private readonly http = inject(HttpClient);

  get(id: string): Observable<PendingActionView> {
    return this.http.get<PendingActionView>(`${API}/pending-actions/${id}`);
  }

  confirm(id: string, cardToken: string | null, key: string): Observable<PendingActionView> {
    return this.http.post<PendingActionView>(`${API}/pending-actions/${id}/confirm`, { cardToken }, idempotent(key));
  }

  reject(id: string, key: string): Observable<PendingActionView> {
    return this.http.post<PendingActionView>(`${API}/pending-actions/${id}/reject`, null, idempotent(key));
  }
}
