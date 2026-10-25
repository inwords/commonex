import {ArgumentsHost, HttpStatus} from '@nestjs/common';
import {FILTER_CATCH_EXCEPTIONS} from '@nestjs/common/constants';
import {AbstractHttpAdapter} from '@nestjs/core';

import {
  ClientUpdateRequiredError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  ExpenseEditingNotSupportedError,
  IdempotencyHashMismatchError,
  IdempotencyRequestInProgressError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

import {BusinessErrorFilter} from '#api/http/filters/business-error.filter';

describe('BusinessErrorFilter', () => {
  it.each([
    [EventNotFoundError, HttpStatus.NOT_FOUND, 'B4001', 'Event not found'],
    [EventDeletedError, HttpStatus.GONE, 'B4002', 'Event is deleted'],
    [InvalidPinCodeError, HttpStatus.FORBIDDEN, 'B4003', 'Invalid pin code'],
    [CurrencyNotFoundError, HttpStatus.NOT_FOUND, 'B4004', 'Currency not found'],
    [CurrencyRateNotFoundError, HttpStatus.NOT_FOUND, 'B4005', 'Currency rate not found'],
    [InvalidTokenError, HttpStatus.UNAUTHORIZED, 'B4008', 'Invalid token'],
    [TokenExpiredError, HttpStatus.UNAUTHORIZED, 'B4009', 'Token has expired'],
    [
      InconsistentExchangedAmountError,
      HttpStatus.BAD_REQUEST,
      'B4010',
      'All splitInfo must have exchangedAmount when custom rate is used',
    ],
    [
      IdempotencyHashMismatchError,
      HttpStatus.UNPROCESSABLE_ENTITY,
      'B4011',
      'Idempotency key reused with different request body',
    ],
    [
      IdempotencyRequestInProgressError,
      HttpStatus.CONFLICT,
      'B4016',
      'Another request with this idempotency key is in progress',
    ],
  ])('maps %p to its status, code and message', (ErrorClass, statusCode, code, message) => {
    const reply = jest.fn();
    const response = {};
    const host = {switchToHttp: () => ({getResponse: (): object => response})} as ArgumentsHost;

    new BusinessErrorFilter({reply} as unknown as AbstractHttpAdapter).catch(new ErrorClass(), host);

    expect(Reflect.getMetadata(FILTER_CATCH_EXCEPTIONS, BusinessErrorFilter)).toContain(ErrorClass);
    expect(reply).toHaveBeenCalledWith(response, {statusCode, code, message}, statusCode);
  });

  it('includes the required protocol version when a client update is required', () => {
    const reply = jest.fn();
    const response = {};
    const host = {switchToHttp: () => ({getResponse: (): object => response})} as ArgumentsHost;

    new BusinessErrorFilter({reply} as unknown as AbstractHttpAdapter).catch(new ClientUpdateRequiredError(2), host);

    expect(reply).toHaveBeenCalledWith(
      response,
      {statusCode: HttpStatus.FORBIDDEN, code: 'B4020', message: 'Client update required', requiredVersion: 2},
      HttpStatus.FORBIDDEN,
    );
  });

  it('maps unsupported expense editing to forbidden', () => {
    const reply = jest.fn();
    const response = {};
    const host = {switchToHttp: () => ({getResponse: (): object => response})} as ArgumentsHost;

    new BusinessErrorFilter({reply} as unknown as AbstractHttpAdapter).catch(
      new ExpenseEditingNotSupportedError(),
      host,
    );

    expect(reply).toHaveBeenCalledWith(
      response,
      {
        statusCode: HttpStatus.FORBIDDEN,
        code: 'B4021',
        message: 'Expense editing is not supported for this event',
      },
      HttpStatus.FORBIDDEN,
    );
  });
});
