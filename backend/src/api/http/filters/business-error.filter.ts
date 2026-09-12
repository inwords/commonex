import {ArgumentsHost, Catch, ExceptionFilter} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';

import {BUSINESS_ERROR_CLASSES, BusinessError} from '#domain/errors/errors';

@Catch(...BUSINESS_ERROR_CLASSES)
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
