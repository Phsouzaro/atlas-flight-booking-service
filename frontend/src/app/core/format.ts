import { Pipe, PipeTransform } from '@angular/core';

import type { Money } from '../api/types';

const BRAZIL = 'America/Sao_Paulo';

export function formatMoney(money: Money | null | undefined): string {
  if (!money) {
    return '';
  }
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: money.currency }).format(money.amount);
}

/** As tools do assistente mandam dinheiro como texto ("BRL 399.90"). */
export function formatMoneyText(text: string | null | undefined): string {
  const match = /^([A-Z]{3}) (-?\d+(?:\.\d+)?)$/.exec(text ?? '');
  return match ? formatMoney({ currency: match[1], amount: Number(match[2]) }) : (text ?? '');
}

/**
 * Data e hora como o backend mandou, sem converter fuso: "2026-10-28T07:00:00-03:00" vira "28/10/2026 07:00".
 * Usado nos horários de voo, que já vêm no fuso do aeroporto.
 */
export function formatLocalDateTime(iso: string | null | undefined): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso ?? '');
  return match ? `${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}` : (iso ?? '');
}

/** Instante em UTC mostrado no horário de Brasília. */
export function formatInstant(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  return new Intl.DateTimeFormat('pt-BR', {
    timeZone: BRAZIL,
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(iso));
}

/** "mm:ss" até `iso`, ou null se já passou. */
export function remaining(iso: string | null | undefined, now: number): string | null {
  if (!iso) {
    return null;
  }
  const seconds = Math.floor((new Date(iso).getTime() - now) / 1000);
  if (seconds <= 0) {
    return null;
  }
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}

export function formatDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}h${m > 0 ? String(m).padStart(2, '0') : ''}` : `${m}min`;
}

const BOOKING_STATUS: Record<string, string> = {
  HELD: 'Aguardando pagamento',
  CONFIRMED: 'Confirmada',
  CANCELLED: 'Cancelada',
  REBOOKED: 'Remarcada',
  EXPIRED: 'Expirada',
};

const ACTION_STATUS: Record<string, string> = {
  PENDING: 'Aguardando sua confirmação',
  CONFIRMED: 'Confirmada',
  REJECTED: 'Rejeitada',
  EXPIRED: 'Expirada',
};

const FARE_CLASS: Record<string, string> = {
  ECONOMY_LIGHT: 'Econômica Light',
  ECONOMY_FLEX: 'Econômica Flex',
  BUSINESS: 'Executiva',
};

export const bookingStatusLabel = (status: string) => BOOKING_STATUS[status] ?? status;
export const actionStatusLabel = (status: string) => ACTION_STATUS[status] ?? status;
export const fareClassLabel = (code: string) => FARE_CLASS[code] ?? code;

@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: Money | string | null | undefined): string {
    return typeof value === 'string' ? formatMoneyText(value) : formatMoney(value);
  }
}

@Pipe({ name: 'localDateTime' })
export class LocalDateTimePipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatLocalDateTime(value);
  }
}

@Pipe({ name: 'instant' })
export class InstantPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatInstant(value);
  }
}
