export const toIsoString = (value: Date | string): string => (value instanceof Date ? value.toISOString() : value);
