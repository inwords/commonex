import {ErrorCode} from './error-codes.enum';

export class EventNotFoundError extends Error {
  override readonly name = 'EventNotFoundError' as const;
  readonly code = ErrorCode.EVENT_NOT_FOUND;

  constructor() {
    super('Event not found');
  }
}

export class EventDeletedError extends Error {
  override readonly name = 'EventDeletedError' as const;
  readonly code = ErrorCode.EVENT_ALREADY_DELETED;

  constructor() {
    super('Event is deleted');
  }
}

export class InvalidPinCodeError extends Error {
  override readonly name = 'InvalidPinCodeError' as const;
  readonly code = ErrorCode.EVENT_INVALID_PIN;

  constructor() {
    super('Invalid pin code');
  }
}

export class InvalidTokenError extends Error {
  override readonly name = 'InvalidTokenError' as const;
  readonly code = ErrorCode.INVALID_TOKEN;

  constructor() {
    super('Invalid token');
  }
}

export class TokenExpiredError extends Error {
  override readonly name = 'TokenExpiredError' as const;
  readonly code = ErrorCode.TOKEN_EXPIRED;

  constructor() {
    super('Token has expired');
  }
}

export class CurrencyNotFoundError extends Error {
  override readonly name = 'CurrencyNotFoundError' as const;
  readonly code = ErrorCode.CURRENCY_NOT_FOUND;

  constructor() {
    super('Currency not found');
  }
}

export class CurrencyRateNotFoundError extends Error {
  override readonly name = 'CurrencyRateNotFoundError' as const;
  readonly code = ErrorCode.CURRENCY_RATE_NOT_FOUND;

  constructor() {
    super('Currency rate not found');
  }
}

export class InconsistentExchangedAmountError extends Error {
  override readonly name = 'InconsistentExchangedAmountError' as const;
  readonly code = ErrorCode.INCONSISTENT_EXCHANGED_AMOUNT;

  constructor() {
    super('All splitInfo must have exchangedAmount when custom rate is used');
  }
}

export class EventOperationConflictError extends Error {
  override readonly name = 'EventOperationConflictError' as const;
  readonly code = ErrorCode.EVENT_OPERATION_CONFLICT;

  constructor() {
    super('Another operation is in progress for this event');
  }
}

export class IdempotencyHashMismatchError extends Error {
  override readonly name = 'IdempotencyHashMismatchError' as const;
  readonly code = ErrorCode.IDEMPOTENCY_HASH_MISMATCH;

  constructor() {
    super('Idempotency key reused with different request body');
  }
}

export class IdempotencyRequestInProgressError extends Error {
  override readonly name = 'IdempotencyRequestInProgressError' as const;
  readonly code = ErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS;

  constructor() {
    super('Another request with this idempotency key is in progress');
  }
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
  IdempotencyRequestInProgressError,
] as const;

export type BusinessError = InstanceType<(typeof BUSINESS_ERROR_CLASSES)[number]>;
