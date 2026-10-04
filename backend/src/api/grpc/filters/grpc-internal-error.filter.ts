import {Metadata} from '@grpc/grpc-js';
import {ArgumentsHost, Catch, Logger, RpcExceptionFilter} from '@nestjs/common';
import {Observable, throwError} from 'rxjs';

import {ErrorCode} from '#domain/errors/error-codes.enum';

import {INTERNAL_ERROR_MESSAGE, createInternalErrorLog} from '#api/filters/internal-error';

import {ERROR_CODE_METADATA_KEY, GRPC_STATUS_BY_ERROR_CODE} from './grpc-status.map';

const getGrpcOperation = (call: unknown): string | undefined => {
  if (typeof call !== 'object' || call === null || !('getPath' in call) || typeof call.getPath !== 'function') {
    return undefined;
  }

  const path = (call as {getPath: () => unknown}).getPath();

  return typeof path === 'string' ? path : undefined;
};

@Catch()
export class GrpcInternalErrorFilter implements RpcExceptionFilter<unknown> {
  private readonly logger = new Logger(GrpcInternalErrorFilter.name);

  catch(exception: unknown, host: ArgumentsHost): Observable<never> {
    const operation = getGrpcOperation(host.getArgByIndex<unknown>(2));
    const metadata = new Metadata();

    metadata.set(ERROR_CODE_METADATA_KEY, ErrorCode.INTERNAL_ERROR);
    this.logger.error(
      createInternalErrorLog(exception, {
        transport: 'grpc',
        ...(operation === undefined ? {} : {operation}),
      }),
    );

    return throwError(() => ({
      code: GRPC_STATUS_BY_ERROR_CODE[ErrorCode.INTERNAL_ERROR],
      details: INTERNAL_ERROR_MESSAGE,
      metadata,
    }));
  }
}
