// Backend falso da demo: responde Ã s mesmas rotas e com os mesmos formatos do backend Spring, mas no navegador.
// As regras que importam sÃ£o as mesmas: hold de 15 min, pagamento com cartÃ£o de teste, cancelamento e remarcaÃ§Ã£o
// sÃ³ por aÃ§Ã£o pendente confirmada pelo usuÃ¡rio, revalidaÃ§Ã£o na confirmaÃ§Ã£o, Idempotency-Key. O que nÃ£o existe
// aqui Ã© o modelo: as respostas do assistente vÃªm de um roteiro (demo-assistant.ts).
import type { BookingView, MessageView, PendingActionView } from '../api/types';
import { DemoAssistant } from './demo-assistant';
import {
  CURRENCY, DemoFlight, addDays, catalogWindowEnd, fareRule, flightById, flightsOn, localDate,
  nextFlightByNumber, SCHEDULE_ZONE,
} from './demo-catalog';
import { Conversation, DemoError, Passenger, moneyText, round2 } from './demo-types';

export { DemoError } from './demo-types';

export interface DemoRequest {
  method: string;
  /** sem o prefixo /api e sem query string */
  path: string;
  body: unknown;
  /** nomes em minÃºsculas */
  headers: Record<string, string | null>;
}

export interface DemoResponse {
  status: number;
  body: unknown;
  headers?: Record<string, string>;
}

interface StoredBooking extends BookingView {
  userId: string;
}

interface StoredAction {
  id: string;
  userId: string;
  type: 'BOOKING_CANCEL' | 'BOOKING_REBOOK';
  status: 'PENDING' | 'CONFIRMED' | 'REJECTED';
  summary: string;
  payload: Record<string, unknown>;
  result: Record<string, unknown> | null;
  createdAt: string;
  expiresAt: string;
  decidedAt: string | null;
}

interface State {
  version: 1;
  session: { userId: string; email: string } | null;
  accounts: Record<string, string>;
  bookings: StoredBooking[];
  actions: StoredAction[];
  conversations: Conversation[];
  messages: Record<string, MessageView[]>;
  /** assentos ocupados pela demo, por voo e classe */
  seats: Record<string, number>;
  replays: Record<string, { request: string; response: DemoResponse }>;
}

export interface CancellationTerms {
  paid: number;
  retained: number;
  refund: number;
  freeWindow: boolean;
  rule: string;
}

export interface RebookingTerms {
  changeFee: number;
  fareDifference: number;
  amountDue: number;
}

const STORAGE_KEY = 'atlas-demo-v1';
const HOLD_TTL_MS = 15 * 60_000;
const ACTION_TTL_MS = 10 * 60_000;
const FREE_WINDOW_MS = 24 * 3_600_000;
const FREE_WINDOW_MIN_ADVANCE_MS = 7 * 86_400_000;
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

const money = (amount: number) => ({ amount: round2(amount), currency: CURRENCY });
const seatKey = (flightId: string, fareClass: string) => `${flightId}|${fareClass}`;

function emptyState(): State {
  return { version: 1, session: null, accounts: {}, bookings: [], actions: [], conversations: [], messages: {}, seats: {}, replays: {} };
}

export class DemoServer {
  private state: State;
  private readonly assistant = new DemoAssistant(this);

  /**
   * @param now     relÃ³gio (os testes passam um controlado)
   * @param storage onde guardar o estado entre recargas; null nos testes
   */
  constructor(private readonly now: () => number = Date.now, private readonly storage: Storage | null = null) {
    this.state = this.load();
  }

  handle(request: DemoRequest): DemoResponse {
    try {
      this.sweep();
      const replayKey = this.replayKey(request);
      if (replayKey) {
        const stored = this.state.replays[replayKey];
        const fingerprint = JSON.stringify(request.body ?? null);
        if (stored) {
          if (stored.request !== fingerprint) {
            throw new DemoError(422, null, 'Idempotency-Key reused with a different request body');
          }
          return { ...stored.response, headers: { ...stored.response.headers, 'Idempotent-Replayed': 'true' } };
        }
        const response = this.route(request);
        if (response.status < 300) {
          this.state.replays[replayKey] = { request: fingerprint, response };
        }
        return response;
      }
      return this.route(request);
    } catch (e) {
      if (e instanceof DemoError) {
        return { status: e.status, body: { type: 'about:blank', status: e.status, detail: e.message, reason: e.reason } };
      }
      throw e;
    } finally {
      if (request.method !== 'GET') {
        this.save();
      }
    }
  }

  // ---- rotas ----------------------------------------------------------------------------------

  private route({ method, path, body }: DemoRequest): DemoResponse {
    const input = (body ?? {}) as Record<string, unknown>;
    let m: RegExpExecArray | null;

    if (method === 'POST' && path === '/auth/login') {
      return ok(this.login(String(input['email'] ?? ''), String(input['password'] ?? '')));
    }
    if (method === 'POST' && path === '/auth/register') {
      const email = String(input['email'] ?? '').trim().toLowerCase();
      if (!/^[^@\s]+@[^@\s]+$/.test(email) || String(input['password'] ?? '').length < 8) {
        throw new DemoError(400, null, 'Invalid email or password');
      }
      if (this.state.accounts[email]) {
        throw new DemoError(409, null, 'Email already registered');
      }
      this.state.accounts[email] = this.id('user');
      return { status: 201, body: { id: this.state.accounts[email], email, role: 'USER' } };
    }
    if (method === 'POST' && path === '/auth/refresh') {
      if (!this.state.session) {
        throw new DemoError(401, null, 'No session');
      }
      return ok(this.token());
    }
    if (method === 'POST' && path === '/auth/logout') {
      this.state.session = null;
      return { status: 204, body: null };
    }

    const user = this.state.session;
    if (!user) {
      throw new DemoError(401, null, 'Unauthorized');
    }

    if (method === 'GET' && path === '/me') {
      return ok({ id: user.userId, email: user.email, role: 'USER' });
    }

    // assistente
    if (method === 'GET' && path === '/assistant/conversations') {
      return ok(this.state.conversations
        .filter((c) => c.userId === user.userId)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
        .map(conversationView));
    }
    if (method === 'POST' && path === '/assistant/conversations') {
      const at = this.iso();
      const conversation: Conversation = { id: this.id('conv'), userId: user.userId, createdAt: at, updatedAt: at, lastFlightIds: [], piiCount: 0 };
      this.state.conversations.push(conversation);
      this.state.messages[conversation.id] = [];
      return { status: 201, body: conversationView(conversation) };
    }
    if ((m = /^\/assistant\/conversations\/([^/]+)\/messages$/.exec(path))) {
      const conversation = this.conversation(m[1], user.userId);
      if (method === 'GET') {
        return ok(this.state.messages[conversation.id] ?? []);
      }
      const content = String(input['content'] ?? '').trim();
      if (!content || content.length > 2000) {
        throw new DemoError(400, null, 'Message must have between 1 and 2000 characters');
      }
      return ok(this.chat(conversation, content));
    }

    // reservas
    if (method === 'GET' && path === '/bookings') {
      return ok(this.bookingsOf(user.userId).map(view));
    }
    if ((m = /^\/bookings\/([^/]+)$/.exec(path)) && method === 'GET') {
      return ok(view(this.booking(m[1], user.userId)));
    }
    if ((m = /^\/bookings\/([^/]+)\/confirm$/.exec(path)) && method === 'POST') {
      return ok(view(this.pay(this.booking(m[1], user.userId), String(input['cardToken'] ?? ''))));
    }
    if ((m = /^\/bookings\/([^/]+)\/release$/.exec(path)) && method === 'POST') {
      return ok(view(this.release(this.booking(m[1], user.userId))));
    }
    if ((m = /^\/bookings\/([^/]+)\/cancellation-quote$/.exec(path)) && method === 'POST') {
      return ok(this.quoteCancellation(user.userId, m[1]).quote);
    }

    // aÃ§Ãµes pendentes
    if ((m = /^\/pending-actions\/([^/]+)$/.exec(path)) && method === 'GET') {
      return ok(this.actionView(this.action(m[1], user.userId)));
    }
    if ((m = /^\/pending-actions\/([^/]+)\/(confirm|reject)$/.exec(path)) && method === 'POST') {
      const action = this.action(m[1], user.userId);
      return ok(m[2] === 'confirm'
        ? this.confirmAction(action, (input['cardToken'] as string | null | undefined) ?? null)
        : this.rejectAction(action));
    }

    throw new DemoError(404, null, `No route ${method} ${path} in the demo`);
  }

  // ---- identidade -----------------------------------------------------------------------------

  /** Na demo qualquer email e senha entram; a conta nasce no primeiro login, jÃ¡ com duas reservas. */
  private login(rawEmail: string, password: string) {
    const email = rawEmail.trim().toLowerCase();
    if (!email || !password) {
      throw new DemoError(401, null, 'Invalid credentials');
    }
    if (!this.state.accounts[email]) {
      this.state.accounts[email] = this.id('user');
    }
    const userId = this.state.accounts[email];
    if (this.bookingsOf(userId).length === 0) {
      this.seedBookings(userId);
    }
    this.state.session = { userId, email };
    return this.token();
  }

  private token() {
    return { accessToken: `demo.${this.id('token')}`, tokenType: 'Bearer', expiresIn: 900 };
  }

  /** Duas reservas pagas para o cancelamento e a remarcaÃ§Ã£o terem com o que trabalhar logo de cara. */
  private seedBookings(userId: string): void {
    const today = localDate(this.now(), SCHEDULE_ZONE);
    const seeds: Array<[string, string, string, number, Passenger[]]> = [
      ['AT120', addDays(today, 12), 'ECONOMY_FLEX', 3,
        [demoPassenger('Ana Souza', '1990-03-12'), demoPassenger('Bruno Lima', '1988-07-01')]],
      ['AT340', addDays(today, 26), 'ECONOMY_LIGHT', 5, [demoPassenger('Ana Souza', '1990-03-12')]],
    ];
    for (const [number, date, fareClass, boughtDaysAgo, passengers] of seeds) {
      const flight = flightById(`${number}_${date}`);
      if (!flight) {
        continue;
      }
      const createdAt = new Date(this.now() - boughtDaysAgo * 86_400_000).toISOString();
      const booking = this.newBooking(userId, flight, fareClass, passengers, createdAt);
      booking.status = 'CONFIRMED';
      booking.confirmedAt = createdAt;
      booking.paymentReference = `SIMP-${this.code()}`;
    }
  }

  // ---- catÃ¡logo e reservas (tambÃ©m usados pelas tools do roteiro) ------------------------------

  /** Voos que ainda nÃ£o partiram, com a disponibilidade descontando o que a demo jÃ¡ reservou. */
  searchFlights(origin: string, destination: string, date: string, pax: number): DemoFlight[] {
    const today = localDate(this.now(), SCHEDULE_ZONE);
    if (date < today) {
      throw new DemoError(400, null, 'Date is in the past');
    }
    if (date > catalogWindowEnd(this.now())) {
      throw new DemoError(400, null, 'Date is outside the simulated catalog (next 60 days)');
    }
    return flightsOn(origin, destination, date)
      .filter((f) => Date.parse(f.departureAt) > this.now())
      .map((f) => this.withAvailability(f))
      .map((f) => ({ ...f, fares: f.fares.filter((fare) => fare.availableSeats >= pax) }))
      .filter((f) => f.fares.length > 0);
  }

  flight(id: string): DemoFlight | null {
    const flight = flightById(id);
    return flight ? this.withAvailability(flight) : null;
  }

  nextFlight(flightNumber: string, fromDate: string): DemoFlight | null {
    const flight = nextFlightByNumber(flightNumber, fromDate, this.now());
    return flight ? this.withAvailability(flight) : null;
  }

  today(): string {
    return localDate(this.now(), SCHEDULE_ZONE);
  }

  bookingsOf(userId: string): StoredBooking[] {
    return this.state.bookings.filter((b) => b.userId === userId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }

  hold(userId: string, flight: DemoFlight, fareClass: string, passengers: Passenger[]): StoredBooking {
    if (passengers.length < 1 || passengers.length > 9) {
      throw new DemoError(400, null, 'A booking has 1 to 9 passengers');
    }
    const fare = this.withAvailability(flight).fares.find((f) => f.fareClass === fareClass);
    if (!fare) {
      throw new DemoError(404, 'FARE_NOT_FOUND', 'Fare not found');
    }
    if (Date.parse(flight.departureAt) <= this.now()) {
      throw new DemoError(409, 'FLIGHT_DEPARTED', 'Flight has already departed');
    }
    if (fare.availableSeats < passengers.length) {
      throw new DemoError(409, 'SEATS_UNAVAILABLE', 'Not enough seats available');
    }
    return this.newBooking(userId, flight, fareClass, passengers, this.iso());
  }

  quoteCancellation(userId: string, bookingId: string) {
    const booking = this.booking(bookingId, userId);
    const terms = this.cancellationTerms(booking);
    const action = this.propose(userId, 'BOOKING_CANCEL',
      `Cancelar a reserva ${booking.code} (voo ${booking.items[0].flightNumber}). Reembolso: ${moneyText(terms.refund)}.`,
      { bookingId: booking.id, refund: terms.refund, currency: CURRENCY });
    const quote = {
      bookingId: booking.id, bookingCode: booking.code, paid: money(terms.paid), retained: money(terms.retained),
      refund: money(terms.refund), freeWindow: terms.freeWindow, rule: terms.rule,
      pendingActionId: action.id, expiresAt: action.expiresAt,
    };
    return { quote, terms, action: this.actionView(action) };
  }

  proposeRebooking(userId: string, bookingId: string, target: DemoFlight, fareClass: string) {
    const booking = this.booking(bookingId, userId);
    const terms = this.rebookingTerms(booking, target, fareClass);
    const fare = this.withAvailability(target).fares.find((f) => f.fareClass === fareClass);
    if (!fare || fare.availableSeats < booking.items[0].seats) {
      throw new DemoError(409, 'SEATS_UNAVAILABLE', 'Not enough seats available on the new flight');
    }
    const action = this.propose(userId, 'BOOKING_REBOOK',
      `Remarcar a reserva ${booking.code} para o voo ${target.flightNumber} (${fareClass}). Valor a pagar: ${moneyText(terms.amountDue)}.`,
      { bookingId: booking.id, newFlightId: target.id, fareClass, amountDue: terms.amountDue, currency: CURRENCY });
    return { terms, action: this.actionView(action) };
  }

  /** Mesma regra do FarePolicy do backend: janela de 24h, depois a regra da classe. */
  cancellationTerms(booking: StoredBooking): CancellationTerms {
    if (booking.status !== 'CONFIRMED') {
      throw new DemoError(409, 'INVALID_STATE', 'Only confirmed bookings can be cancelled');
    }
    const item = booking.items[0];
    if (Date.parse(item.departureAt) <= this.now()) {
      throw new DemoError(409, 'FLIGHT_DEPARTED', 'Flight has already departed');
    }
    const paid = booking.total.amount;
    const purchasedAt = booking.rebookedFromId ? null : Date.parse(booking.confirmedAt ?? booking.createdAt);
    const freeWindow = purchasedAt !== null && this.now() < purchasedAt + FREE_WINDOW_MS
      && Date.parse(item.departureAt) >= purchasedAt + FREE_WINDOW_MIN_ADVANCE_MS;
    if (freeWindow) {
      return { paid, retained: 0, refund: paid, freeWindow: true,
        rule: 'Cancelamento em atÃ© 24h da compra, feita com 7 dias ou mais de antecedÃªncia: reembolso integral' };
    }
    const rule = fareRule(item.fareClass)!;
    if (!rule.refundable) {
      return { paid, retained: paid, refund: 0, freeWindow: false, rule: `Tarifa ${rule.fareClass} nÃ£o Ã© reembolsÃ¡vel` };
    }
    const refund = Math.max(0, round2(paid - rule.cancellationFee * item.seats));
    return { paid, retained: round2(paid - refund), refund, freeWindow: false,
      rule: `Tarifa ${rule.fareClass} reembolsÃ¡vel com multa de ${rule.cancellationFee.toFixed(2)} ${CURRENCY} por passageiro` };
  }

  rebookingTerms(booking: StoredBooking, target: DemoFlight, fareClass: string): RebookingTerms {
    if (booking.status !== 'CONFIRMED') {
      throw new DemoError(409, 'INVALID_STATE', 'Only confirmed bookings can be rebooked');
    }
    const item = booking.items[0];
    if (Date.parse(item.departureAt) <= this.now()) {
      throw new DemoError(409, 'FLIGHT_DEPARTED', 'Current flight has already departed');
    }
    if (Date.parse(target.departureAt) <= this.now()) {
      throw new DemoError(409, 'FLIGHT_DEPARTED', 'New flight has already departed');
    }
    if (item.origin !== target.origin || item.destination !== target.destination) {
      throw new DemoError(422, 'REBOOKING_NOT_ALLOWED', 'Rebooking must keep the same route');
    }
    const targetFare = target.fares.find((f) => f.fareClass === fareClass);
    if (!targetFare) {
      throw new DemoError(404, 'FARE_NOT_FOUND', 'Fare not found');
    }
    const changeFee = fareRule(item.fareClass)!.changeFee * item.seats;
    const fareDifference = round2((targetFare.price - item.unitPrice.amount) * item.seats);
    return { changeFee, fareDifference, amountDue: round2(changeFee + Math.max(0, fareDifference)) };
  }

  // ---- execuÃ§Ã£o: sÃ³ por endpoint chamado pela UI ------------------------------------------------

  private pay(booking: StoredBooking, cardToken: string): StoredBooking {
    if (booking.status === 'EXPIRED') {
      throw new DemoError(409, 'HOLD_EXPIRED', 'Hold expired');
    }
    if (booking.status !== 'HELD') {
      throw new DemoError(409, 'INVALID_STATE', 'Booking is not on hold');
    }
    this.charge(cardToken);
    booking.status = 'CONFIRMED';
    booking.confirmedAt = this.iso();
    booking.paymentReference = `SIMP-${this.code()}`;
    return booking;
  }

  private release(booking: StoredBooking): StoredBooking {
    if (booking.status !== 'HELD') {
      throw new DemoError(409, 'INVALID_STATE', 'Only bookings on hold can be released');
    }
    booking.status = 'CANCELLED';
    booking.cancelledAt = this.iso();
    booking.refund = money(0);
    this.returnSeats(booking);
    return booking;
  }

  private confirmAction(action: StoredAction, cardToken: string | null): PendingActionView {
    this.requirePending(action);
    const booking = this.booking(String(action.payload['bookingId']), action.userId);
    let result: StoredBooking;
    if (action.type === 'BOOKING_CANCEL') {
      const terms = this.cancellationTerms(booking);
      if (terms.refund !== action.payload['refund']) {
        throw new DemoError(409, 'QUOTE_CHANGED', 'Refund changed; request a new quote');
      }
      booking.status = 'CANCELLED';
      booking.cancelledAt = this.iso();
      booking.refund = money(terms.refund);
      this.returnSeats(booking);
      result = booking;
    } else {
      const target = this.flight(String(action.payload['newFlightId']));
      const fareClass = String(action.payload['fareClass']);
      if (!target) {
        throw new DemoError(404, 'FARE_NOT_FOUND', 'Flight not found');
      }
      const terms = this.rebookingTerms(booking, target, fareClass);
      if (terms.amountDue !== action.payload['amountDue']) {
        throw new DemoError(409, 'QUOTE_CHANGED', 'Amount due changed; request a new proposal');
      }
      if (terms.amountDue > 0) {
        if (!cardToken) {
          throw new DemoError(422, 'PAYMENT_REQUIRED', 'This rebooking has an amount due');
        }
        this.charge(cardToken);
      }
      const fare = target.fares.find((f) => f.fareClass === fareClass)!;
      if (fare.availableSeats < booking.items[0].seats) {
        throw new DemoError(409, 'SEATS_UNAVAILABLE', 'Not enough seats available on the new flight');
      }
      const at = this.iso();
      const replacement = this.newBooking(action.userId, target, fareClass, booking.passengers, at);
      replacement.status = 'CONFIRMED';
      replacement.confirmedAt = at;
      replacement.paymentReference = terms.amountDue > 0 ? `SIMP-${this.code()}` : null;
      replacement.rebookedFromId = booking.id;
      booking.status = 'REBOOKED';
      booking.rebookedAt = at;
      booking.rebookedToId = replacement.id;
      this.returnSeats(booking);
      result = replacement;
    }
    action.status = 'CONFIRMED';
    action.decidedAt = this.iso();
    action.result = view(result) as unknown as Record<string, unknown>;
    return this.actionView(action);
  }

  private rejectAction(action: StoredAction): PendingActionView {
    this.requirePending(action);
    action.status = 'REJECTED';
    action.decidedAt = this.iso();
    return this.actionView(action);
  }

  /** Pagamento simulado, como o SimulatedPaymentGateway: tok_decline recusa, o resto aprova. */
  private charge(cardToken: string): void {
    if (!cardToken) {
      throw new DemoError(400, null, 'cardToken is required');
    }
    if (cardToken === 'tok_decline') {
      throw new DemoError(402, 'PAYMENT_DECLINED', 'Payment declined');
    }
  }

  // ---- assistente -------------------------------------------------------------------------------

  private chat(conversation: Conversation, content: string) {
    const at = this.iso();
    const { masked, reply } = this.assistant.reply(conversation, content);
    const messageId = this.id('msg');
    const messages = this.state.messages[conversation.id] ?? (this.state.messages[conversation.id] = []);
    messages.push({ id: this.id('msg'), role: 'USER', content: masked, createdAt: at, toolCalls: [],
      pendingActionIds: [], attachments: { flights: [], bookings: [] } });
    messages.push({ id: messageId, role: 'ASSISTANT', content: reply.content, createdAt: at, toolCalls: reply.toolCalls,
      pendingActionIds: reply.pendingActions.map((a) => a.id), attachments: reply.attachments });
    conversation.updatedAt = at;
    return { conversationId: conversation.id, messageId, ...reply, promptVersion: 'demo-script',
      usage: { promptTokens: 0, completionTokens: 0 } };
  }

  // ---- apoio ------------------------------------------------------------------------------------

  private newBooking(userId: string, flight: DemoFlight, fareClass: string, passengers: Passenger[], createdAt: string): StoredBooking {
    const fare = flight.fares.find((f) => f.fareClass === fareClass)!;
    const booking: StoredBooking = {
      userId,
      id: this.id('booking'),
      code: this.code(),
      status: 'HELD',
      total: money(fare.price * passengers.length),
      createdAt,
      holdExpiresAt: new Date(Date.parse(createdAt) + HOLD_TTL_MS).toISOString(),
      confirmedAt: null, expiredAt: null, cancelledAt: null, rebookedAt: null, paymentReference: null,
      refund: null, rebookedFromId: null, rebookedToId: null,
      items: [{
        flightId: flight.id, flightNumber: flight.flightNumber, origin: flight.origin, destination: flight.destination,
        departureAt: flight.departureAt, fareClass, seats: passengers.length, unitPrice: money(fare.price),
      }],
      passengers,
    };
    this.state.bookings.push(booking);
    const key = seatKey(flight.id, fareClass);
    this.state.seats[key] = (this.state.seats[key] ?? 0) + passengers.length;
    return booking;
  }

  private returnSeats(booking: StoredBooking): void {
    const item = booking.items[0];
    const key = seatKey(item.flightId, item.fareClass);
    this.state.seats[key] = Math.max(0, (this.state.seats[key] ?? 0) - item.seats);
  }

  private withAvailability(flight: DemoFlight): DemoFlight {
    return {
      ...flight,
      fares: flight.fares.map((f) => ({ ...f, availableSeats: Math.max(0, f.availableSeats - (this.state.seats[seatKey(flight.id, f.fareClass)] ?? 0)) })),
    };
  }

  private propose(userId: string, type: StoredAction['type'], summary: string, payload: Record<string, unknown>): StoredAction {
    const action: StoredAction = {
      id: this.id('action'), userId, type, status: 'PENDING', summary, payload, result: null,
      createdAt: this.iso(), expiresAt: new Date(this.now() + ACTION_TTL_MS).toISOString(), decidedAt: null,
    };
    this.state.actions.push(action);
    return action;
  }

  /** EXPIRED Ã© derivado, como no backend: a aÃ§Ã£o pendente vencida nÃ£o muda no banco. */
  actionView(action: StoredAction): PendingActionView {
    const expired = action.status === 'PENDING' && Date.parse(action.expiresAt) <= this.now();
    const { userId: _, ...rest } = action;
    return structuredClone({ ...rest, status: expired ? 'EXPIRED' : action.status });
  }

  private requirePending(action: StoredAction): void {
    if (action.status !== 'PENDING' || Date.parse(action.expiresAt) <= this.now()) {
      throw new DemoError(409, 'ACTION_CLOSED', 'Pending action is no longer pending');
    }
  }

  /** O job de expiraÃ§Ã£o do backend, aqui rodando a cada request. */
  private sweep(): void {
    for (const booking of this.state.bookings) {
      if (booking.status === 'HELD' && Date.parse(booking.holdExpiresAt) <= this.now()) {
        booking.status = 'EXPIRED';
        booking.expiredAt = booking.holdExpiresAt;
        this.returnSeats(booking);
      }
    }
  }

  private booking(id: string, userId: string): StoredBooking {
    const booking = this.state.bookings.find((b) => b.id === id && b.userId === userId);
    if (!booking) {
      throw new DemoError(404, null, 'Booking not found');
    }
    return booking;
  }

  private action(id: string, userId: string): StoredAction {
    const action = this.state.actions.find((a) => a.id === id && a.userId === userId);
    if (!action) {
      throw new DemoError(404, null, 'Pending action not found');
    }
    return action;
  }

  private conversation(id: string, userId: string): Conversation {
    const conversation = this.state.conversations.find((c) => c.id === id && c.userId === userId);
    if (!conversation) {
      throw new DemoError(404, null, 'Conversation not found');
    }
    return conversation;
  }

  private replayKey(request: DemoRequest): string | null {
    const key = request.headers['idempotency-key'];
    return request.method === 'POST' && key ? `${this.state.session?.userId ?? '-'}|${request.path}|${key}` : null;
  }

  private iso(): string {
    return new Date(this.now()).toISOString();
  }

  private id(prefix: string): string {
    return `${prefix}-${crypto.randomUUID()}`;
  }

  private code(): string {
    const values = crypto.getRandomValues(new Uint32Array(6));
    return Array.from(values, (v) => CODE_ALPHABET[v % CODE_ALPHABET.length]).join('');
  }

  private load(): State {
    try {
      const raw = this.storage?.getItem(STORAGE_KEY);
      const parsed = raw ? (JSON.parse(raw) as State) : null;
      return parsed?.version === 1 ? parsed : emptyState();
    } catch {
      return emptyState();
    }
  }

  private save(): void {
    try {
      this.storage?.setItem(STORAGE_KEY, JSON.stringify(this.state));
    } catch {
      // sem storage a demo continua funcionando, sÃ³ nÃ£o sobrevive a um recarregamento
    }
  }
}

function ok(body: unknown): DemoResponse {
  return { status: 200, body: structuredClone(body) };
}

function view(booking: StoredBooking): BookingView {
  const { userId: _, ...rest } = booking;
  return structuredClone(rest);
}

function conversationView(c: Conversation) {
  return { id: c.id, createdAt: c.createdAt, updatedAt: c.updatedAt };
}

function demoPassenger(fullName: string, birthDate: string): Passenger {
  return { fullName, documentNumber: '[demo]', birthDate };
}

