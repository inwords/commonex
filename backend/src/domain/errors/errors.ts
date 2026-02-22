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
