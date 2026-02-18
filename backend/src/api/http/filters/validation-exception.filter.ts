import {ArgumentsHost, BadRequestException, Catch, ExceptionFilter, HttpStatus} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';
import {ErrorCode} from '#domain/errors/error-codes.enum';

@Catch(BadRequestException)
export class ValidationExceptionFilter implements ExceptionFilter {
  constructor(private readonly httpAdapter: AbstractHttpAdapter) {}

  private formatMessage(msg: unknown): string {
    if (typeof msg === 'string') {
      return msg;
    }

    if (Array.isArray(msg)) {
      return msg.join('; ');
    }

    if (typeof msg === 'object' && msg !== null) {
      return JSON.stringify(msg);
    }

    return String(msg);
  }

  catch(exception: BadRequestException, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const exceptionResponse = exception.getResponse() as {message: unknown};

    const validationErrors = this.formatMessage(exceptionResponse.message);

    this.httpAdapter.reply(
      ctx.getResponse(),
      {
        statusCode: HttpStatus.BAD_REQUEST,
        code: ErrorCode.VALIDATION_ERROR,
        message: validationErrors,
      },
      HttpStatus.BAD_REQUEST,
    );
  }
}
