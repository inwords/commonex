import {getCurrentDateWithoutTimeUTC, getDateWithoutTimeUTC} from '#packages/date-utils';

describe('date-utils', () => {
  it('formats the UTC calendar date with zero padding', () => {
    expect(getDateWithoutTimeUTC(new Date('2024-03-05T10:00:00Z'))).toBe('2024-03-05');
  });

  it('uses the UTC date, not the local one', () => {
    expect(getDateWithoutTimeUTC(new Date('2024-03-05T23:30:00-05:00'))).toBe('2024-03-06');
  });

  it('returns today in UTC', () => {
    jest.useFakeTimers({now: new Date('2026-01-06T23:59:59Z')});

    expect(getCurrentDateWithoutTimeUTC()).toBe('2026-01-06');

    jest.useRealTimers();
  });
});
