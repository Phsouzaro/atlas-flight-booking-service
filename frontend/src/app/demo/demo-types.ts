import { CURRENCY } from './demo-catalog';

/** Erro de negócio no formato problem+json, com o mesmo `reason` do backend. */
export class DemoError extends Error {
  constructor(readonly status: number, readonly reason: string | null, detail: string) {
    super(detail);
  }
}

export interface Passenger {
  fullName: string;
  documentNumber: string;
  birthDate: string;
}

export interface Conversation {
  id: string;
  userId: string;
  createdAt: string;
  updatedAt: string;
  /** voos da última busca, para "quero o AT120" achar o dia certo */
  lastFlightIds: string[];
  /** quantos tokens de PII já foram emitidos (o [DOC_1], [DOC_2]...) */
  piiCount: number;
}

export const round2 = (n: number) => Math.round(n * 100) / 100;

/** Como o backend escreve dinheiro nos resumos e nas tools: "BRL 558.90". */
export const moneyText = (amount: number) => `${CURRENCY} ${round2(amount).toFixed(2)}`;
