// Roteiro do assistente da demo. Não há modelo: a intenção sai de expressões regulares e a resposta é montada
// com os mesmos resultados de tool que o backend devolve (voos, reservas, propostas). Ele obedece às mesmas
// regras do assistente real: só consulta, segura assentos e propõe; quem confirma é o usuário, pela UI.
import type { Attachments, BookingInfo, FlightInfo, PendingActionView } from '../api/types';
import { DemoFlight, addDays, airport, fareRule, hasRoute, localIso, routeList } from './demo-catalog';
import type { DemoServer } from './demo-server';
import { Conversation, DemoError, Passenger, moneyText } from './demo-types';

export interface ScriptedReply {
  content: string;
  toolCalls: string[];
  pendingActions: PendingActionView[];
  attachments: Attachments;
}

type Booking = ReturnType<DemoServer['bookingsOf']>[number];

const brl = (amount: number) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(amount);
const dateBr = (date: string) => `${date.slice(8, 10)}/${date.slice(5, 7)}/${date.slice(0, 4)}`;

const ALIASES: Array<[RegExp, string]> = [
  [/\bGRU\b|guarulhos/i, 'GRU'], [/\bCGH\b|congonhas/i, 'CGH'], [/\bGIG\b|gale[aã]o/i, 'GIG'],
  [/\bSDU\b|santos dumont/i, 'SDU'], [/\bBSB\b|bras[ií]lia/i, 'BSB'], [/\bCNF\b|confins|belo horizonte|\bBH\b/i, 'CNF'],
  [/\bSSA\b|salvador/i, 'SSA'], [/\bREC\b|recife/i, 'REC'], [/\bPOA\b|porto alegre/i, 'POA'], [/\bMIA\b|miami/i, 'MIA'],
  [/\bJFK\b|nova york|new york/i, 'JFK'], [/\bLIS\b|lisboa|lisbon/i, 'LIS'], [/s[aã]o paulo|\bSP\b/i, 'GRU'],
  [/\brio\b/i, 'GIG'],
];

const NUMBERS: Record<string, number> = { um: 1, uma: 1, dois: 2, duas: 2, tres: 3, 'três': 3, quatro: 4, cinco: 5, seis: 6, sete: 7, oito: 8, nove: 9 };

const POLICIES: Record<string, string> = {
  bagagem: 'Pela política de bagagem: todas as classes incluem uma bagagem de mão de até 10 kg (55 x 35 x 25 cm) e '
    + 'um item pessoal. Despachada: a Econômica Light não inclui; a Econômica Flex inclui uma mala de 23 kg; a '
    + 'Executiva, duas de 32 kg. Power banks e baterias vão sempre na bagagem de mão.',
  cancelamento: 'Pela política de cancelamento: até 24 horas depois do pagamento o reembolso é integral, se a compra '
    + 'foi feita com 7 dias ou mais de antecedência. Fora dessa janela vale a classe: Light não devolve nada, Flex '
    + 'devolve o valor pago menos R$ 150,00 por passageiro e Executiva devolve tudo. O cancelamento é em duas etapas: '
    + 'eu cotei, você confirma no card.',
  remarcacao: 'Pela política de remarcação: dá para trocar para outro voo da mesma rota, em outra data ou classe. '
    + 'Custa a multa da classe atual (R$ 250,00 por passageiro na Light; Flex e Executiva não têm multa) mais a '
    + 'diferença de tarifa, se o voo novo for mais caro. Diferença a menor não é devolvida.',
  tarifas: 'Temos três classes: Econômica Light (mais barata, não reembolsável, só bagagem de mão, remarcação com '
    + 'multa de R$ 250,00), Econômica Flex (reembolsável com multa de R$ 150,00, remarcação sem multa, uma mala de '
    + '23 kg) e Executiva (reembolso integral, remarcação sem multa, duas malas de 32 kg e embarque prioritário).',
  pagamento: 'O pagamento é feito por você, na tela Minhas reservas, com cartão de teste: a reserva fica segura por '
    + '15 minutos sem cobrança. Eu não peço nem recebo número de cartão e não consigo pagar, cancelar ou remarcar '
    + 'sozinho: só consulto, seguro assentos e preparo propostas para você confirmar.',
};

export class DemoAssistant {
  constructor(private readonly server: DemoServer) {}

  /** Responde a uma mensagem. `masked` é o texto como fica guardado: documentos viram [DOC_1] etc. */
  reply(conversation: Conversation, text: string): { masked: string; reply: ScriptedReply } {
    const pii = this.mask(conversation, text);
    try {
      return { masked: pii.masked, reply: this.route(conversation, text, pii) };
    } catch (e) {
      if (e instanceof DemoError) {
        return { masked: pii.masked, reply: say(this.explain(e)) };
      }
      throw e;
    }
  }

  private route(conversation: Conversation, text: string, pii: Pii): ScriptedReply {
    const t = text.toLowerCase();
    if (/ignor|esque[cç]a (as |suas )?(regras|instru)|instru[cç][oõ]es anteriores|system prompt|prompt do sistema|sem (me )?(pedir|perguntar|confirma)|modo (admin|desenvolvedor)|cancele tudo|ignore/.test(t)) {
      return say('Isso eu não faço. Não tenho nenhuma ferramenta que pague, cancele ou remarque: só consulto, seguro '
        + 'assentos e preparo propostas. Qualquer mudança numa reserva só acontece quando você clica em Confirmar '
        + 'num card, e o backend recalcula tudo antes de executar.');
    }
    if (/\b(pague|pagar|paga|cobre|cobrar)\b/.test(t) && !/como|onde|posso|regra|pol[ií]tica/.test(t)) {
      const held = this.bookings(conversation).filter((b) => b.status === 'HELD');
      return {
        ...say(`Eu não consigo pagar. ${held.length ? 'O pagamento da reserva é feito por você: use o botão Pagar no card abaixo, com um cartão de teste.' : 'Você não tem reserva aguardando pagamento.'}`),
        toolCalls: ['getMyBookings'],
        attachments: { flights: [], bookings: held.map((b) => this.bookingInfo(b)) },
      };
    }
    const policy = this.policyTopic(t);
    if (policy) {
      return { ...say(POLICIES[policy]), toolCalls: ['searchPolicies'] };
    }
    if (/cancel/.test(t)) {
      return this.cancel(conversation, text);
    }
    if (/remarc|trocar (o |de )?voo|mudar (o |de )?voo|alterar (a )?(data|reserva)/.test(t)) {
      return this.rebook(conversation, text);
    }
    if (/\bat\s?\d{3}\b/.test(t) || /passageir\w*\s*:|\bcpf\b|nascid/.test(t)) {
      return this.hold(conversation, text, pii);
    }
    if (/reserva/.test(t)) {
      const list = this.bookings(conversation);
      return {
        ...say(list.length ? `Você tem ${list.length} ${list.length === 1 ? 'reserva' : 'reservas'}:` : 'Você ainda não tem reservas.'),
        toolCalls: ['getMyBookings'],
        attachments: { flights: [], bookings: list.map((b) => this.bookingInfo(b)) },
      };
    }
    if (/voo|voar|passage|viajar|ir (para|pra)|flight/.test(t) || this.airports(text).length > 0) {
      return this.search(conversation, text);
    }
    return say('Na demo eu entendo pedidos como: "voos de GRU para Lisboa no dia 20", "quais são as minhas reservas?", '
      + '"quero cancelar a reserva mais antiga", "quero remarcar uma reserva" e perguntas sobre bagagem e tarifas. '
      + 'Pode também tentar me convencer a cancelar algo sem confirmação.');
  }

  // ---- intenções --------------------------------------------------------------------------------

  private search(conversation: Conversation, text: string): ScriptedReply {
    const codes = this.airports(text);
    let origin = 'GRU';
    let destination = 'GIG';
    let assumed = codes.length === 0;
    if (codes.length >= 2) {
      [origin, destination] = codes;
    } else if (codes.length === 1) {
      destination = codes[0] === 'GRU' ? 'GIG' : codes[0];
      assumed = true;
    }
    if (!hasRoute(origin, destination)) {
      return say(`Não há voos ${origin} → ${destination} no catálogo simulado. Rotas disponíveis (nos dois sentidos): ${routeList().join(', ')}.`);
    }
    const date = this.date(text) ?? addDays(this.server.today(), 7);
    const pax = this.pax(text);
    const flights = this.server.searchFlights(origin, destination, date, pax);
    conversation.lastFlightIds = flights.map((f) => f.id);
    const route = `${origin} (${airport(origin)!.city}) → ${destination} (${airport(destination)!.city})`;
    const intro = assumed ? 'Como a rota não ficou clara, busquei ' : 'Busquei ';
    const content = flights.length
      ? `${intro}${route} em ${dateBr(date)}, para ${pax} ${pax === 1 ? 'passageiro' : 'passageiros'}. `
        + `Encontrei ${flights.length} ${flights.length === 1 ? 'voo' : 'voos'}. Escolha uma tarifa no card para eu segurar os assentos.`
      : `Não encontrei voos ${route} em ${dateBr(date)} com assento para ${pax}. Quer tentar outra data?`;
    return {
      ...say(content),
      toolCalls: ['searchFlights'],
      attachments: { flights: flights.map((f) => this.flightInfo(f)), bookings: [] },
    };
  }

  private hold(conversation: Conversation, text: string, pii: Pii): ScriptedReply {
    const number = /\bAT\s?(\d{3})\b/i.exec(text)?.[1];
    const fare = this.fare(text);
    if (!number || !fare) {
      return say('Para segurar assentos preciso do voo e da tarifa. Busque um voo e use o botão Escolher no card.');
    }
    const known = conversation.lastFlightIds.map((id) => this.server.flight(id)).find((f) => f?.flightNumber === `AT${number}`);
    const flight = known ?? this.server.nextFlight(`AT${number}`, this.date(text) ?? this.server.today());
    if (!flight) {
      return say(`Não achei o voo AT${number} nos próximos 60 dias.`);
    }
    const passengers = this.passengers(text, pii);
    if (passengers.length === 0) {
      return say(`Para segurar o voo ${flight.flightNumber} (${fareRule(fare)!.name}) preciso do nome completo, do `
        + 'documento e da data de nascimento de cada passageiro. Por exemplo: "Passageiros: Ana Souza, CPF '
        + '529.982.247-25, nascida em 12/03/1990". Na demo nenhum dado sai do seu navegador.');
    }
    const booking = this.server.hold(conversation.userId, flight, fare, passengers);
    const tokens = pii.tokens.filter((t) => t.startsWith('[DOC'));
    const piiNote = tokens.length
      ? ` O documento chegou para mim como ${tokens.join(', ')}: o número verdadeiro só é usado dentro do backend, na hora de criar a reserva.`
      : '';
    return {
      ...say(`Segurei ${passengers.length} ${passengers.length === 1 ? 'assento' : 'assentos'} no voo ${flight.flightNumber} `
        + `(${fareRule(fare)!.name}), total de ${brl(booking.total.amount)}. A reserva ${booking.code} fica segura por 15 minutos: `
        + `o pagamento é com você, no botão Pagar.${piiNote}`),
      toolCalls: ['createBookingHold'],
      attachments: { flights: [], bookings: [this.bookingInfo(booking)] },
    };
  }

  private cancel(conversation: Conversation, text: string): ScriptedReply {
    const target = this.targetBooking(conversation, text);
    if (!target) {
      return { ...say('Você não tem reserva confirmada para cancelar.'), toolCalls: ['getMyBookings'] };
    }
    if (target.status === 'HELD') {
      return { ...say(`A reserva ${target.code} ainda não foi paga, então não precisa de cancelamento: ela expira sozinha `
        + 'em 15 minutos, ou você pode desistir dela agora na tela Minhas reservas.'), toolCalls: ['getMyBookings'] };
    }
    const { terms, action } = this.server.quoteCancellation(conversation.userId, target.id);
    const rule = terms.freeWindow ? 'você está na janela de 24 horas, então o reembolso é integral'
      : terms.refund === 0 ? `a tarifa ${fareRule(target.items[0].fareClass)!.name} não é reembolsável`
        : `a tarifa ${fareRule(target.items[0].fareClass)!.name} tem multa de ${brl(fareRule(target.items[0].fareClass)!.cancellationFee)} por passageiro`;
    return {
      ...say(`Cotei o cancelamento da reserva ${target.code} (voo ${target.items[0].flightNumber}). Você pagou `
        + `${brl(terms.paid)} e ${rule}: o reembolso seria de ${brl(terms.refund)}. Nada foi cancelado. Se quiser seguir, `
        + 'confirme no card abaixo; a proposta vale por 10 minutos.'),
      toolCalls: ['getMyBookings', 'quoteCancellation'],
      pendingActions: [action],
    };
  }

  private rebook(conversation: Conversation, text: string): ScriptedReply {
    const target = this.targetBooking(conversation, text);
    if (!target || target.status !== 'CONFIRMED') {
      return { ...say('Só reservas confirmadas podem ser remarcadas, e não achei nenhuma.'), toolCalls: ['getMyBookings'] };
    }
    const item = target.items[0];
    const currentDate = localIso(Date.parse(item.departureAt), airport(item.origin)!.timeZone).slice(0, 10);
    const date = this.date(text) ?? addDays(currentDate, 1);
    const options = this.server.searchFlights(item.origin, item.destination, date, item.seats);
    const fare = this.fare(text) ?? item.fareClass;
    const wanted = /\bAT\s?(\d{3})\b/i.exec(text)?.[1];
    const flight = options.find((f) => f.flightNumber === `AT${wanted}`) ?? options.find((f) => f.flightNumber === item.flightNumber) ?? options[0];
    if (!flight || !flight.fares.some((f) => f.fareClass === fare)) {
      return { ...say(`Não achei voo ${item.origin} → ${item.destination} em ${dateBr(date)} com assento na mesma tarifa. Quer tentar outra data?`),
        toolCalls: ['getMyBookings', 'searchFlights'] };
    }
    const { terms, action } = this.server.proposeRebooking(conversation.userId, target.id, flight, fare);
    const difference = terms.fareDifference > 0 ? `diferença de tarifa de ${brl(terms.fareDifference)}`
      : terms.fareDifference < 0 ? 'o voo novo é mais barato, mas a diferença não é devolvida' : 'sem diferença de tarifa';
    return {
      ...say(`Preparei a remarcação da reserva ${target.code} para o voo ${flight.flightNumber} de `
        + `${dateBr(flight.departureLocal.slice(0, 10))} às ${flight.departureLocal.slice(11, 16)} (${fareRule(fare)!.name}). `
        + `Multa: ${brl(terms.changeFee)}; ${difference}. Total a pagar: ${brl(terms.amountDue)}. Nada mudou ainda: `
        + 'confirme no card abaixo se quiser seguir.'),
      toolCalls: ['getMyBookings', 'searchFlights', 'proposeRebooking'],
      pendingActions: [action],
    };
  }

  // ---- leitura do texto -------------------------------------------------------------------------

  /** Pergunta sobre regra (vai para a busca de políticas), não pedido de ação. */
  private policyTopic(t: string): string | null {
    if (/bagag|mala/.test(t)) {
      return 'bagagem';
    }
    const asksRule = /regra|pol[ií]tica|como funciona|quanto custa|inclui|diferen[cç]a entre|como (eu )?pag/.test(t);
    const wantsAction = /(quero|preciso|gostaria|pode|poderia|favor).{0,25}(cancel|remarc)/.test(t);
    if (!asksRule || wantsAction) {
      return null;
    }
    if (/cancel|reembols/.test(t)) {
      return 'cancelamento';
    }
    if (/remarc/.test(t)) {
      return 'remarcacao';
    }
    if (/pag/.test(t)) {
      return 'pagamento';
    }
    return 'tarifas';
  }

  /** Aeroportos na ordem em que aparecem: o primeiro é a origem. */
  private airports(text: string): string[] {
    const found: Array<{ index: number; code: string }> = [];
    for (const [pattern, code] of ALIASES) {
      const match = pattern.exec(text);
      if (match) {
        found.push({ index: match.index, code });
      }
    }
    return [...new Set(found.sort((a, b) => a.index - b.index).map((f) => f.code))];
  }

  private date(text: string): string | null {
    const today = this.server.today();
    let m: RegExpExecArray | null;
    if ((m = /(\d{4})-(\d{2})-(\d{2})/.exec(text))) {
      return m[0];
    }
    if ((m = /\b(\d{1,2})\/(\d{1,2})(?:\/(\d{4}))?\b/.exec(text))) {
      const year = m[3] ?? today.slice(0, 4);
      const date = `${year}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`;
      return !m[3] && date < today ? `${Number(year) + 1}${date.slice(4)}` : date;
    }
    if ((m = /\bdia (\d{1,2})\b/i.exec(text))) {
      const day = Number(m[1]);
      for (let d = 0; d < 62; d++) {
        const candidate = addDays(today, d);
        if (Number(candidate.slice(8, 10)) === day) {
          return candidate;
        }
      }
    }
    if (/amanh[ãa]/i.test(text)) {
      return addDays(today, 1);
    }
    if (/\bhoje\b/i.test(text)) {
      return today;
    }
    if (/semana que vem|pr[oó]xima semana/i.test(text)) {
      return addDays(today, 7);
    }
    return null;
  }

  private pax(text: string): number {
    if (/s[oó] eu|sozinh/i.test(text)) {
      return 1;
    }
    const m = /\b(\d|um|uma|dois|duas|tr[eê]s|quatro|cinco|seis|sete|oito|nove)\s+(passageir|pessoa|adult)/i.exec(text);
    if (!m) {
      return 1;
    }
    return /\d/.test(m[1]) ? Math.min(9, Math.max(1, Number(m[1]))) : NUMBERS[m[1].toLowerCase()] ?? 1;
  }

  private fare(text: string): string | null {
    const t = text.toLowerCase();
    return /light/.test(t) ? 'ECONOMY_LIGHT' : /flex/.test(t) ? 'ECONOMY_FLEX' : /executiva|business/.test(t) ? 'BUSINESS' : null;
  }

  /** Nomes próprios depois de "Passageiros:"; cada um leva o documento e a data na mesma ordem. */
  private passengers(text: string, pii: Pii): Passenger[] {
    const section = /passageir\w*\s*:(.*)$/is.exec(text)?.[1] ?? '';
    const names = section.match(/[A-ZÁÉÍÓÚÂÊÔÃÕÇ][a-záéíóúâêôãõç]+(?:\s+(?:d[aeo]s?\s+)?[A-ZÁÉÍÓÚÂÊÔÃÕÇ][a-záéíóúâêôãõç]+)+/g) ?? [];
    const dates = [...section.matchAll(/(\d{2})\/(\d{2})\/(\d{4})|(\d{4}-\d{2}-\d{2})/g)]
      .map((m) => m[4] ?? `${m[3]}-${m[2]}-${m[1]}`);
    return names.slice(0, 9).map((fullName, i) => ({
      fullName,
      documentNumber: pii.documents[i] ?? '[não informado]',
      birthDate: dates[i] ?? '1990-01-01',
    }));
  }

  /** A reserva pelo código citado; senão "mais antiga"; senão a confirmada mais recente. */
  private targetBooking(conversation: Conversation, text: string): Booking | null {
    const list = this.bookings(conversation);
    const byCode = list.find((b) => new RegExp(`\\b${b.code}\\b`, 'i').test(text));
    if (byCode) {
      return byCode;
    }
    const confirmed = list.filter((b) => b.status === 'CONFIRMED');
    if (/antiga|primeira/i.test(text)) {
      return confirmed.at(-1) ?? null;
    }
    return confirmed[0] ?? null;
  }

  private bookings(conversation: Conversation): Booking[] {
    return this.server.bookingsOf(conversation.userId);
  }

  // ---- PII --------------------------------------------------------------------------------------

  /** Como o PiiDetector + PiiVault: o texto guardado (e o que iria para o modelo) só tem tokens. */
  private mask(conversation: Conversation, text: string): Pii {
    const documents: string[] = [];
    const tokens: string[] = [];
    const next = (kind: string) => `[${kind}_${++conversation.piiCount}]`;
    let masked = text.replace(/\b(?:\d[ -]?){13,19}\b/g, () => '[CARTAO_REMOVIDO]');
    masked = masked.replace(/\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b/g, (doc) => {
      const token = next('DOC');
      documents.push(doc);
      tokens.push(token);
      return token;
    });
    masked = masked.replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, () => {
      const token = next('EMAIL');
      tokens.push(token);
      return token;
    });
    return { masked, documents, tokens };
  }

  // ---- formatos das tools (ToolViews do backend) ------------------------------------------------

  private flightInfo(f: DemoFlight): FlightInfo {
    return {
      flightId: f.id,
      flightNumber: f.flightNumber,
      origin: `${f.origin} (${airport(f.origin)!.city})`,
      destination: `${f.destination} (${airport(f.destination)!.city})`,
      departureLocal: f.departureLocal,
      arrivalLocal: f.arrivalLocal,
      durationMinutes: f.durationMinutes,
      fares: f.fares.map((fare) => ({
        fareClass: fare.fareClass, name: fare.name, availableSeats: fare.availableSeats, pricePerPassenger: moneyText(fare.price),
      })),
    };
  }

  private bookingInfo(b: Booking): BookingInfo {
    const item = b.items[0];
    return {
      bookingId: b.id,
      code: b.code,
      status: b.status,
      flightNumber: item.flightNumber,
      route: `${item.origin} -> ${item.destination}`,
      departureLocal: localIso(Date.parse(item.departureAt), airport(item.origin)!.timeZone),
      fareClass: item.fareClass,
      passengers: b.passengers.length,
      passengerNames: b.passengers.map((p) => shortName(p.fullName)),
      total: moneyText(b.total.amount),
      holdExpiresAt: b.holdExpiresAt,
    };
  }

  private explain(e: DemoError): string {
    switch (e.reason) {
      case 'SEATS_UNAVAILABLE': return 'Não há mais assentos suficientes nesse voo. Quer que eu busque outro horário?';
      case 'FLIGHT_DEPARTED': return 'Esse voo já partiu.';
      case 'REBOOKING_NOT_ALLOWED': return 'A remarcação precisa manter a mesma rota.';
      case 'INVALID_STATE': return 'Essa reserva não está num estado que permita isso.';
    }
    return /past/.test(e.message) ? 'Essa data já passou. Escolha uma data a partir de hoje.'
      : /outside/.test(e.message) ? 'O catálogo simulado cobre só os próximos 60 dias.'
        : 'Não consegui fazer isso agora.';
  }
}

interface Pii {
  masked: string;
  documents: string[];
  tokens: string[];
}

function say(content: string): ScriptedReply {
  return { content, toolCalls: [], pendingActions: [], attachments: { flights: [], bookings: [] } };
}

function shortName(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  return parts.length === 1 ? parts[0] : `${parts[0]} ${parts[parts.length - 1][0]}.`;
}

