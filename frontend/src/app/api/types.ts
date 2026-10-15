// Aliases dos tipos gerados do contrato do backend (schema.ts, via `npm run api:generate`).
// Ajustes só onde o OpenAPI não consegue expressar o que o backend manda de verdade.
import type { components } from './schema';

type S = components['schemas'];

export type Money = S['Money'];
export type TokenResponse = S['TokenResponse'];
export type UserResponse = S['UserResponse'];
export type ConversationView = S['ConversationView'];
export type MessageView = S['MessageView'];
export type AssistantReply = S['AssistantReply'];
export type Attachments = S['Attachments'];
export type FlightInfo = S['FlightInfo'];
export type FareInfo = S['FareInfo'];
export type BookingInfo = S['BookingInfo'];
export type CancellationQuote = S['CancellationQuote'];
export type BookingItem = S['Item'];

/** `refund` é null até a reserva ser cancelada; o 3.0 não permite nullable ao lado de $ref. */
export type BookingView = Omit<S['BookingView'], 'refund'> & { refund: Money | null };
export type BookingStatus = BookingView['status'];

/** payload e result são JSON livre (Map<String, Object> no backend). */
export type PendingActionView = Omit<S['PendingActionView'], 'payload' | 'result'> & {
  payload: Record<string, unknown>;
  result: Record<string, unknown> | null;
};
export type PendingActionStatus = PendingActionView['status'];
