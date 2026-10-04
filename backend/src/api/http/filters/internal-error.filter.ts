import {ArgumentsHost, Catch, HttpException, HttpStatus, Logger} from '@nestjs/common';
import {AbstractHttpAdapter, BaseExceptionFilter} from '@nestjs/core';

import {ErrorCode} from '#domain/errors/error-codes.enum';

import {INTERNAL_ERROR_MESSAGE, InternalErrorLogContext, createInternalErrorLog} from '#api/filters/internal-error';

@Catch()
export class InternalErrorFilter extends BaseExceptionFilter<unknown> {
  private readonly logger = new Logger(InternalErrorFilter.name);

  constructor(private readonly httpAdapter: AbstractHttpAdapter) {
    super(httpAdapter);
  }

  override catch(exception: unknown, host: ArgumentsHost): void {
    if (exception instanceof HttpException) {
      super.catch(exception, host);
      return;
    }

    const ctx = host.switchToHttp();
    const request = ctx.getRequest<{id?: unknown; method?: unknown; url?: unknown}>();
    const method = typeof request.method === 'string' ? request.method : undefined;
    const url = typeof request.url === 'string' ? request.url : undefined;
    const operation = method !== undefined && url !== undefined ? `${method} ${url}` : (method ?? url);
    const requestId = typeof request.id === 'string' ? request.id : undefined;
    const logContext: InternalErrorLogContext = {
      transport: 'http',
      ...(operation === undefined ? {} : {operation}),
      ...(requestId === undefined ? {} : {requestId}),
    };

    this.logger.error(createInternalErrorLog(exception, logContext));
    this.httpAdapter.reply(
      ctx.getResponse(),
      {
        statusCode: HttpStatus.INTERNAL_SERVER_ERROR,
        code: ErrorCode.INTERNAL_ERROR,
        message: INTERNAL_ERROR_MESSAGE,
      },
      HttpStatus.INTERNAL_SERVER_ERROR,
    );
  }
}
