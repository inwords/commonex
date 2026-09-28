import {HttpStatus} from '@nestjs/common';

import {ErrorCode} from './error-codes.enum';

export class EventNotFoundError {
  readonly name = 'EventNotFoundError' as const;
  readonly code = ErrorCode.EVENT_NOT_FOUND;
  readonly message = 'Event not found';
  readonly httpCode = HttpStatus.NOT_FOUND;
}

export class EventDeletedError {
  readonly name = 'EventDeletedError' as const;
  readonly code = ErrorCode.EVENT_ALREADY_DELETED;
  readonly message = 'Event is deleted';
  readonly httpCode = HttpStatus.GONE;
}

export class InvalidPinCodeError {
  readonly name = 'InvalidPinCodeError' as const;
  readonly code = ErrorCode.EVENT_INVALID_PIN;
  readonly message = 'Invalid pin code';
  readonly httpCode = HttpStatus.FORBIDDEN;
}

export class InvalidTokenError {
  readonly name = 'InvalidTokenError' as const;
  readonly code = ErrorCode.INVALID_TOKEN;
  readonly message = 'Invalid token';
  readonly httpCode = HttpStatus.UNAUTHORIZED;
}

export class TokenExpiredError {
  readonly name = 'TokenExpiredError' as const;
  readonly code = ErrorCode.TOKEN_EXPIRED;
  readonly message = 'Token has expired';
  readonly httpCode = HttpStatus.UNAUTHORIZED;
}

export class CurrencyNotFoundError {
  readonly name = 'CurrencyNotFoundError' as const;
  readonly code = ErrorCode.CURRENCY_NOT_FOUND;
  readonly message = 'Currency not found';
  readonly httpCode = HttpStatus.NOT_FOUND;
}

export class CurrencyRateNotFoundError {
  readonly name = 'CurrencyRateNotFoundError' as const;
  readonly code = ErrorCode.CURRENCY_RATE_NOT_FOUND;
  readonly message = 'Currency rate not found';
  readonly httpCode = HttpStatus.NOT_FOUND;
}

export class InconsistentExchangedAmountError {
  readonly name = 'InconsistentExchangedAmountError' as const;
  readonly code = ErrorCode.INCONSISTENT_EXCHANGED_AMOUNT;
  readonly message = 'All splitInfo must have exchangedAmount when custom rate is used';
  readonly httpCode = HttpStatus.BAD_REQUEST;
}

export class EventOperationConflictError {
  readonly name = 'EventOperationConflictError' as const;
  readonly code = ErrorCode.EVENT_OPERATION_CONFLICT;
  readonly message = 'Another operation is in progress for this event';
  readonly httpCode = HttpStatus.CONFLICT;
}

export class ExpenseReferenceNotFoundError {
  readonly name = 'ExpenseReferenceNotFoundError' as const;
  readonly code = ErrorCode.EXPENSE_REFERENCE_NOT_FOUND;
  readonly message = 'Referenced expense not found in event';
  readonly httpCode = HttpStatus.BAD_REQUEST;
}

export class ExpenseAlreadyRevertedError {
  readonly name = 'ExpenseAlreadyRevertedError' as const;
  readonly code = ErrorCode.EXPENSE_ALREADY_REVERTED;
  readonly message = 'Expense is already reverted';
  readonly httpCode = HttpStatus.CONFLICT;
}

export class ExpenseCorrectionConflictError {
  readonly name = 'ExpenseCorrectionConflictError' as const;
  readonly code = ErrorCode.EXPENSE_CORRECTION_CONFLICT;
  readonly message = 'Expense correction is invalid';
  readonly httpCode = HttpStatus.BAD_REQUEST;
}

export class IdempotencyHashMismatchError {
  readonly name = 'IdempotencyHashMismatchError' as const;
  readonly code = ErrorCode.IDEMPOTENCY_HASH_MISMATCH;
  readonly message = 'Idempotency key reused with different request body';
  readonly httpCode = HttpStatus.UNPROCESSABLE_ENTITY;
}

/** Every domain error the transport layers translate; both exception filters catch exactly this list. */
export const BUSINESS_ERROR_CLASSES = [
  EventNotFoundError,
  EventDeletedError,
  EventOperationConflictError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  InconsistentExchangedAmountError,
  IdempotencyHashMismatchError,
  ExpenseReferenceNotFoundError,
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
] as const;

export type BusinessError = InstanceType<(typeof BUSINESS_ERROR_CLASSES)[number]>;
