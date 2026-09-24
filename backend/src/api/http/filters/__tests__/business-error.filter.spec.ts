import {ArgumentsHost, HttpStatus} from '@nestjs/common';
import {FILTER_CATCH_EXCEPTIONS} from '@nestjs/common/constants';
import {AbstractHttpAdapter} from '@nestjs/core';

import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  EventOperationConflictError,
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
  ExpenseReferenceNotFoundError,
  IdempotencyHashMismatchError,
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
    [ExpenseReferenceNotFoundError, HttpStatus.BAD_REQUEST, 'B4012', 'Referenced expense not found in event'],
    [ExpenseAlreadyRevertedError, HttpStatus.CONFLICT, 'B4013', 'Expense is already reverted'],
    [ExpenseCorrectionConflictError, HttpStatus.BAD_REQUEST, 'B4014', 'Expense correction is invalid'],
    [EventOperationConflictError, HttpStatus.CONFLICT, 'B4015', 'Another operation is in progress for this event'],
  ])('maps %p to its status, code and message', (ErrorClass, statusCode, code, message) => {
    const reply = jest.fn();
    const response = {};
    const host = {switchToHttp: () => ({getResponse: (): object => response})} as ArgumentsHost;

    new BusinessErrorFilter({reply} as unknown as AbstractHttpAdapter).catch(new ErrorClass(), host);

    expect(Reflect.getMetadata(FILTER_CATCH_EXCEPTIONS, BusinessErrorFilter)).toContain(ErrorClass);
    expect(reply).toHaveBeenCalledWith(response, {statusCode, code, message}, statusCode);
  });
});
