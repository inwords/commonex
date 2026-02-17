import {ArgumentsHost, Catch, ExceptionFilter} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

type BusinessError =
  | EventNotFoundError
  | EventDeletedError
  | InvalidPinCodeError
  | InvalidTokenError
  | TokenExpiredError
  | CurrencyNotFoundError
  | CurrencyRateNotFoundError;

@Catch(
  EventNotFoundError,
  EventDeletedError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
)
export class BusinessErrorFilter implements ExceptionFilter {
  constructor(private readonly httpAdapter: AbstractHttpAdapter) {}

  catch(exception: BusinessError, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();

    this.httpAdapter.reply(
      ctx.getResponse(),
      {
        statusCode: exception.httpCode,
        code: exception.code,
        message: exception.message,
      },
      exception.httpCode,
    );
  }
}
