// An idempotency-key replay returns the cached response round-tripped through the `idempotency_keys.response` jsonb
// column, so `createdAt`/`updatedAt` arrive as ISO strings rather than `Date` instances despite the `IExpense` type.
export const toIsoString = (value: Date | string): string =>
  (value instanceof Date ? value : new Date(value)).toISOString();
