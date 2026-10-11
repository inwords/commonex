import {error, success} from '#packages/result';

import {EventNotFoundError} from '#domain/errors';

import {unwrapResult} from '#api/unwrap-result';

describe('unwrapResult', () => {
  it('returns the original successful value', () => {
    const value = {createdAt: new Date('2026-01-01T00:00:00Z')};

    expect(unwrapResult(success<typeof value, Error>(value))).toBe(value);
  });

  it('throws the original business error', () => {
    const failure = new EventNotFoundError();

    expect.assertions(1);
    try {
      unwrapResult(error(failure));
    } catch (caught) {
      expect(caught).toBe(failure);
    }
  });
});
