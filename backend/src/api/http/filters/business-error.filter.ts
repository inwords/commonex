import {ArgumentsHost, Catch, ExceptionFilter, HttpStatus} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';

import {ErrorCode} from '#domain/errors/error-codes.enum';
import {BUSINESS_ERROR_CLASSES, BusinessError} from '#domain/errors/errors';

const HTTP_STATUS_BY_ERROR_CODE: Partial<Record<ErrorCode, HttpStatus>> = {
  [ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS]: HttpStatus.CONFLICT,
};

const getHttpStatus = (exception: BusinessError): HttpStatus => {
  if ('httpCode' in exception) {
    return exception.httpCode;
  }

  const status = HTTP_STATUS_BY_ERROR_CODE[exception.code];
  if (status === undefined) {
    throw new Error(`Missing HTTP status mapping for business error ${exception.code}`);
  }

  return status;
};

@Catch(...BUSINESS_ERROR_CLASSES)
export class BusinessErrorFilter implements ExceptionFilter {
  constructor(private readonly httpAdapter: AbstractHttpAdapter) {}

  catch(exception: BusinessError, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const statusCode = getHttpStatus(exception);

    this.httpAdapter.reply(
      ctx.getResponse(),
      {
        statusCode,
        code: exception.code,
        message: exception.message,
      },
      statusCode,
    );
  }
}
