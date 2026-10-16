// Catálogo da demo: as mesmas rotas, horários, classes e preços do CatalogSeeder do backend, gerados no navegador.
// Tudo é função da data (mesma data, mesmos voos e ids); a ocupação vem de um hash, não de Math.random.

export interface DemoAirport {
  code: string;
  city: string;
  timeZone: string;
}

export interface FareRule {
  fareClass: string;
  name: string;
  refundable: boolean;
  /** por passageiro, em BRL (V7__fare_rules.sql) */
  cancellationFee: number;
  changeFee: number;
}

export interface DemoFare {
  fareClass: string;
  name: string;
  totalSeats: number;
  availableSeats: number;
  price: number;
}

export interface DemoFlight {
  id: string;
  flightNumber: string;
  origin: string;
  destination: string;
  /** instantes em UTC */
  departureAt: string;
  arrivalAt: string;
  /** os mesmos instantes no fuso de cada aeroporto, como o backend manda */
  departureLocal: string;
  arrivalLocal: string;
  durationMinutes: number;
  fares: DemoFare[];
}

export const CURRENCY = 'BRL';
export const SCHEDULE_ZONE = 'America/Sao_Paulo';
const DAYS = 60;

export const AIRPORTS: DemoAirport[] = [
  { code: 'GRU', city: 'São Paulo', timeZone: 'America/Sao_Paulo' },
  { code: 'CGH', city: 'São Paulo', timeZone: 'America/Sao_Paulo' },
  { code: 'GIG', city: 'Rio de Janeiro', timeZone: 'America/Sao_Paulo' },
  { code: 'SDU', city: 'Rio de Janeiro', timeZone: 'America/Sao_Paulo' },
  { code: 'BSB', city: 'Brasília', timeZone: 'America/Sao_Paulo' },
  { code: 'CNF', city: 'Belo Horizonte', timeZone: 'America/Sao_Paulo' },
  { code: 'SSA', city: 'Salvador', timeZone: 'America/Bahia' },
  { code: 'REC', city: 'Recife', timeZone: 'America/Recife' },
  { code: 'POA', city: 'Porto Alegre', timeZone: 'America/Sao_Paulo' },
  { code: 'MIA', city: 'Miami', timeZone: 'America/New_York' },
  { code: 'JFK', city: 'Nova York', timeZone: 'America/New_York' },
  { code: 'LIS', city: 'Lisboa', timeZone: 'Europe/Lisbon' },
];

export const FARE_RULES: FareRule[] = [
  { fareClass: 'ECONOMY_LIGHT', name: 'Econômica Light', refundable: false, cancellationFee: 0, changeFee: 250 },
  { fareClass: 'ECONOMY_FLEX', name: 'Econômica Flex', refundable: true, cancellationFee: 150, changeFee: 0 },
  { fareClass: 'BUSINESS', name: 'Executiva', refundable: true, cancellationFee: 0, changeFee: 0 },
];

/** Assentos (doméstico, internacional) e multiplicador de preço, como no seeder. */
const SEATS: Record<string, [number, number, number]> = {
  ECONOMY_LIGHT: [120, 200, 1.0],
  ECONOMY_FLEX: [36, 60, 1.4],
  BUSINESS: [12, 28, 3.2],
};

interface Route {
  origin: string;
  destination: string;
  minutes: number;
  basePrice: number;
  international: boolean;
  times: string[];
}

const ROUTES: Route[] = [];
function pair(a: string, b: string, minutes: number, basePrice: number, international: boolean,
              outbound: string[], inbound: string[]): void {
  ROUTES.push({ origin: a, destination: b, minutes, basePrice, international, times: outbound });
  ROUTES.push({ origin: b, destination: a, minutes, basePrice, international, times: inbound });
}
pair('CGH', 'SDU', 60, 349, false, ['07:00', '12:00', '18:30'], ['08:30', '13:30', '20:00']);
pair('GRU', 'GIG', 65, 399, false, ['07:30', '19:00'], ['09:00', '20:30']);
pair('GRU', 'BSB', 100, 529, false, ['08:00', '17:00'], ['10:30', '19:30']);
pair('GRU', 'SSA', 145, 689, false, ['09:15'], ['13:00']);
pair('GRU', 'REC', 190, 799, false, ['10:00'], ['14:30']);
pair('GRU', 'POA', 95, 489, false, ['06:45', '16:30'], ['09:00', '19:00']);
pair('CGH', 'CNF', 70, 429, false, ['07:15', '18:00'], ['09:00', '19:45']);
pair('GIG', 'BSB', 105, 549, false, ['08:30'], ['11:30']);
pair('GIG', 'SSA', 125, 619, false, ['11:00'], ['14:15']);
pair('BSB', 'REC', 150, 659, false, ['14:00'], ['17:45']);
pair('GRU', 'MIA', 515, 3290, true, ['23:10'], ['20:30']);
pair('GRU', 'JFK', 590, 3890, true, ['22:00'], ['21:15']);
pair('GRU', 'LIS', 610, 3590, true, ['21:30'], ['11:45']);
pair('GIG', 'LIS', 590, 3490, true, ['20:45'], ['12:30']);

export const airport = (code: string) => AIRPORTS.find((a) => a.code === code);
export const fareRule = (fareClass: string) => FARE_RULES.find((f) => f.fareClass === fareClass);
export const hasRoute = (origin: string, destination: string) =>
  ROUTES.some((r) => r.origin === origin && r.destination === destination);
export const routeList = () => ROUTES.filter((r, i) => i % 2 === 0).map((r) => `${r.origin}-${r.destination}`);

// ---- tempo ------------------------------------------------------------------------------------

/** Diferença em minutos entre o fuso e UTC naquele instante. */
function offsetMinutes(timeZone: string, utcMs: number): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).formatToParts(utcMs);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const asUtc = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'));
  return Math.round((asUtc - Math.floor(utcMs / 1000) * 1000) / 60_000);
}

/** "2026-10-28T07:30:00-03:00": o instante no fuso do aeroporto. */
export function localIso(utcMs: number, timeZone: string): string {
  const offset = offsetMinutes(timeZone, utcMs);
  const local = new Date(utcMs + offset * 60_000).toISOString().slice(0, 19);
  const sign = offset < 0 ? '-' : '+';
  const abs = Math.abs(offset);
  return `${local}${sign}${String(Math.floor(abs / 60)).padStart(2, '0')}:${String(abs % 60).padStart(2, '0')}`;
}

/** Data local (YYYY-MM-DD) de um instante num fuso. */
export function localDate(utcMs: number, timeZone: string): string {
  return localIso(utcMs, timeZone).slice(0, 10);
}

export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T12:00:00Z`) + days * 86_400_000).toISOString().slice(0, 10);
}

function localToUtc(date: string, time: string, timeZone: string): number {
  const guess = Date.parse(`${date}T${time}:00Z`);
  return guess - offsetMinutes(timeZone, guess) * 60_000;
}

// ---- voos -------------------------------------------------------------------------------------

function hash(text: string): number {
  let h = 2166136261;
  for (let i = 0; i < text.length; i++) {
    h = Math.imul(h ^ text.charCodeAt(i), 16777619);
  }
  return h >>> 0;
}

function build(route: Route, routeIndex: number, timeIndex: number, date: string): DemoFlight {
  const flightNumber = `AT${100 + routeIndex * 10 + timeIndex}`;
  const departure = localToUtc(date, route.times[timeIndex], airport(route.origin)!.timeZone);
  const arrival = departure + route.minutes * 60_000;
  const fares = FARE_RULES.map((rule) => {
    const [domestic, international, multiplier] = SEATS[rule.fareClass];
    const total = route.international ? international : domestic;
    // ocupação fixa para a data; no máximo 90%, para a demo não abrir com tudo lotado
    const taken = hash(`${flightNumber}:${date}:${rule.fareClass}`) % Math.floor(total * 0.9);
    return {
      fareClass: rule.fareClass,
      name: rule.name,
      totalSeats: total,
      availableSeats: total - taken,
      price: Math.round(route.basePrice * multiplier) + 0.9,
    };
  });
  return {
    id: `${flightNumber}_${date}`,
    flightNumber,
    origin: route.origin,
    destination: route.destination,
    departureAt: new Date(departure).toISOString(),
    arrivalAt: new Date(arrival).toISOString(),
    departureLocal: localIso(departure, airport(route.origin)!.timeZone),
    arrivalLocal: localIso(arrival, airport(route.destination)!.timeZone),
    durationMinutes: route.minutes,
    fares,
  };
}

/** Voos da rota no dia (data local da origem), em ordem de partida. Inclui os que já partiram. */
export function flightsOn(origin: string, destination: string, date: string): DemoFlight[] {
  const index = ROUTES.findIndex((r) => r.origin === origin && r.destination === destination);
  if (index < 0) {
    return [];
  }
  return ROUTES[index].times.map((_, t) => build(ROUTES[index], index, t, date));
}

/** Voo pelo id da demo ("AT120_2026-10-28"). */
export function flightById(id: string): DemoFlight | null {
  const match = /^AT(\d{3})_(\d{4}-\d{2}-\d{2})$/.exec(id);
  if (!match) {
    return null;
  }
  const n = Number(match[1]) - 100;
  const route = ROUTES[Math.floor(n / 10)];
  if (!route || n % 10 >= route.times.length) {
    return null;
  }
  return build(route, Math.floor(n / 10), n % 10, match[2]);
}

/** Primeiro voo com esse número a partir de `fromDate`, dentro da janela do catálogo. */
export function nextFlightByNumber(flightNumber: string, fromDate: string, now: number): DemoFlight | null {
  for (let d = 0; d < DAYS; d++) {
    const flight = flightById(`${flightNumber.toUpperCase()}_${addDays(fromDate, d)}`);
    if (flight && Date.parse(flight.departureAt) > now) {
      return flight;
    }
  }
  return null;
}

export function catalogWindowEnd(now: number): string {
  return addDays(localDate(now, SCHEDULE_ZONE), DAYS - 1);
}
