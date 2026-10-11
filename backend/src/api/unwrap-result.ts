import {Result, isError} from '#packages/result';

export const unwrapResult = <T, E extends Error>(result: Result<T, E>): T => {
  if (isError(result)) {
    throw result.error;
  }

  return result.value;
};
