import {error, isError, isSuccess, success} from '#packages/result';

describe('result', () => {
  it('wraps a value as success', () => {
    const result = success<number, string>(1);

    expect(result).toEqual({result: 'success', value: 1});
    expect(isSuccess(result)).toBe(true);
    expect(isError(result)).toBe(false);
  });

  it('wraps an error as error', () => {
    const result = error<number, string>('boom');

    expect(result).toEqual({result: 'error', error: 'boom'});
    expect(isError(result)).toBe(true);
    expect(isSuccess(result)).toBe(false);
  });
});
