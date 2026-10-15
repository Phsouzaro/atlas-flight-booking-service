import { HttpErrorResponse } from '@angular/common/http';

/** Erro da API em application/problem+json (RFC 9457), com o `reason` que os módulos de negócio mandam. */
export interface Problem {
  status: number;
  detail: string | null;
  reason: string | null;
  /** do header Retry-After, quando o backend limitou a requisição (429) */
  retryAfterSeconds: number | null;
}

export function problemOf(error: unknown): Problem {
  if (error instanceof HttpErrorResponse) {
    const body = (error.error ?? {}) as { detail?: unknown; reason?: unknown };
    const retryAfter = Number(error.headers?.get('Retry-After'));
    return {
      status: error.status,
      detail: typeof body.detail === 'string' ? body.detail : null,
      reason: typeof body.reason === 'string' ? body.reason : null,
      retryAfterSeconds: Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : null,
    };
  }
  return { status: 0, detail: null, reason: null, retryAfterSeconds: null };
}

function waitText(seconds: number | null): string {
  if (!seconds) {
    return 'daqui a pouco';
  }
  if (seconds < 60) {
    return `em ${seconds} s`;
  }
  return `em ${Math.ceil(seconds / 60)} min`;
}

const REASONS: Record<string, string> = {
  SEATS_UNAVAILABLE: 'Não há mais assentos suficientes nesse voo.',
  FLIGHT_DEPARTED: 'Esse voo já partiu.',
  FARE_NOT_FOUND: 'Voo ou tarifa não encontrados.',
  INVALID_STATE: 'A reserva não está mais nesse estado. Atualize a página.',
  HOLD_EXPIRED: 'O prazo de 15 minutos para pagar acabou e os assentos foram liberados.',
  PAYMENT_DECLINED: 'Pagamento recusado. Tente outro cartão.',
  PAYMENT_REQUIRED: 'Essa ação tem valor a pagar: escolha um cartão.',
  QUOTE_CHANGED: 'Os valores mudaram desde a cotação. Peça uma nova cotação.',
  REBOOKING_NOT_ALLOWED: 'Essa remarcação não é permitida.',
  ACTION_CLOSED: 'Essa proposta já foi decidida ou expirou.',
};

/** Mensagem em português para mostrar ao usuário. */
export function describeError(error: unknown): string {
  const problem = problemOf(error);
  if (problem.reason && REASONS[problem.reason]) {
    return REASONS[problem.reason];
  }
  switch (problem.status) {
    case 429:
      return `Muitas tentativas seguidas. Tente de novo ${waitText(problem.retryAfterSeconds)}.`;
    case 0:
      return 'Sem conexão com o servidor. Tente de novo.';
    case 401:
      return 'Sua sessão expirou. Entre de novo.';
    case 404:
      return 'Não encontrado.';
    case 503:
      return 'O assistente está indisponível no momento. Tente de novo em instantes.';
    default:
      return problem.detail ?? 'Algo deu errado. Tente de novo.';
  }
}
