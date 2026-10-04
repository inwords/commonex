import {trace} from '@opentelemetry/api';

export const INTERNAL_ERROR_MESSAGE = 'Internal server error';

export interface InternalErrorLogContext {
  transport: 'grpc' | 'http';
  operation?: string;
  requestId?: string;
}

export const createInternalErrorLog = (exception: unknown, context: InternalErrorLogContext): object => {
  const spanContext = trace.getActiveSpan()?.spanContext();
  const isError = exception instanceof Error;

  return {
    event: 'transport.internal_error',
    ...context,
    ...(spanContext === undefined ? {} : {traceId: spanContext.traceId, spanId: spanContext.spanId}),
    exception,
    exceptionName: isError ? exception.name : typeof exception,
    exceptionMessage: isError
      ? exception.message
      : typeof exception === 'string'
        ? exception
        : 'Non-Error value thrown',
    ...(isError && exception.stack !== undefined ? {stack: exception.stack} : {}),
    ...(isError && exception.cause !== undefined ? {cause: exception.cause} : {}),
  };
};
