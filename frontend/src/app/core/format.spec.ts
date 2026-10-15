import { formatDuration, formatLocalDateTime, formatMoney, formatMoneyText, remaining } from './format';

describe('format', () => {
  it('formats money in Brazilian real', () => {
    expect(formatMoney({ amount: 399.9, currency: 'BRL' }).replace(/\s/g, ' ')).toBe('R$ 399,90');
    expect(formatMoneyText('BRL 1277.90').replace(/\s/g, ' ')).toBe('R$ 1.277,90');
    expect(formatMoneyText('não é dinheiro')).toBe('não é dinheiro');
  });

  it('keeps the airport time zone of flight times instead of converting to the browser', () => {
    expect(formatLocalDateTime('2026-10-28T07:00:00-03:00')).toBe('28/10/2026 07:00');
    expect(formatLocalDateTime('2026-10-29T11:40:00+01:00')).toBe('29/10/2026 11:40');
  });

  it('counts down until a deadline and stops at zero', () => {
    const now = Date.parse('2026-10-15T12:00:00Z');
    expect(remaining('2026-10-15T12:14:05Z', now)).toBe('14:05');
    expect(remaining('2026-10-15T12:00:00Z', now)).toBeNull();
    expect(remaining(null, now)).toBeNull();
  });

  it('formats durations', () => {
    expect(formatDuration(65)).toBe('1h05');
    expect(formatDuration(120)).toBe('2h');
    expect(formatDuration(45)).toBe('45min');
  });
});
