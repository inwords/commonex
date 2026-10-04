import {Span, trace} from '@opentelemetry/api';

import {createInternalErrorLog} from '#api/filters/internal-error';

describe('createInternalErrorLog', () => {
  it('keeps the original exception, stack, cause and active trace context', () => {
    const cause = new Error('driver connection failed');
    const exception = new Error('query failed', {cause});

    jest.spyOn(trace, 'getActiveSpan').mockReturnValue({
      spanContext: () => ({traceId: 'trace-id', spanId: 'span-id', traceFlags: 1}),
    } as Span);

    expect(
      createInternalErrorLog(exception, {
        transport: 'http',
        operation: 'GET /user/event/example',
        requestId: 'request-id',
      }),
    ).toEqual({
      event: 'transport.internal_error',
      transport: 'http',
      operation: 'GET /user/event/example',
      requestId: 'request-id',
      traceId: 'trace-id',
      spanId: 'span-id',
      exception,
      exceptionName: 'Error',
      exceptionMessage: 'query failed',
      stack: exception.stack,
      cause,
    });
  });

  it('keeps a non-Error thrown value without inventing stack or cause fields', () => {
    jest.spyOn(trace, 'getActiveSpan').mockReturnValue(undefined);

    expect(createInternalErrorLog('failure', {transport: 'grpc'})).toEqual({
      event: 'transport.internal_error',
      transport: 'grpc',
      exception: 'failure',
      exceptionName: 'string',
      exceptionMessage: 'failure',
    });
  });
});
