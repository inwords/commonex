import {ArgumentsHost, Catch, ExceptionFilter, HttpStatus} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';

import {ErrorCode} from '#domain/errors/error-codes.enum';
import {BUSINESS_ERROR_CLASSES, BusinessError} from '#domain/errors/errors';

const HTTP_STATUS_BY_ERROR_CODE = {
  [ErrorCode.EVENT_NOT_FOUND]: HttpStatus.NOT_FOUND,
  [ErrorCode.EVENT_ALREADY_DELETED]: HttpStatus.GONE,
  [ErrorCode.EVENT_INVALID_PIN]: HttpStatus.FORBIDDEN,
  [ErrorCode.EVENT_OPERATION_CONFLICT]: HttpStatus.CONFLICT,
  [ErrorCode.CURRENCY_NOT_FOUND]: HttpStatus.NOT_FOUND,
  [ErrorCode.CURRENCY_RATE_NOT_FOUND]: HttpStatus.NOT_FOUND,
  [ErrorCode.INCONSISTENT_EXCHANGED_AMOUNT]: HttpStatus.BAD_REQUEST,
  [ErrorCode.INVALID_TOKEN]: HttpStatus.UNAUTHORIZED,
  [ErrorCode.TOKEN_EXPIRED]: HttpStatus.UNAUTHORIZED,
  [ErrorCode.IDEMPOTENCY_HASH_MISMATCH]: HttpStatus.UNPROCESSABLE_ENTITY,
  [ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS]: HttpStatus.CONFLICT,
} satisfies Record<BusinessError['code'], HttpStatus>;

const getHttpStatus = (exception: BusinessError): HttpStatus => {
  return HTTP_STATUS_BY_ERROR_CODE[exception.code];
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
