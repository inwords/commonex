import {status} from '@grpc/grpc-js';

import {ErrorCode} from '#domain/errors/error-codes.enum';

export const GRPC_STATUS_BY_ERROR_CODE: Record<ErrorCode, status> = {
  [ErrorCode.EVENT_NOT_FOUND]: status.NOT_FOUND,
  [ErrorCode.EVENT_ALREADY_DELETED]: status.FAILED_PRECONDITION,
  [ErrorCode.EVENT_INVALID_PIN]: status.PERMISSION_DENIED,
  [ErrorCode.EVENT_OPERATION_CONFLICT]: status.ABORTED,
  [ErrorCode.CURRENCY_NOT_FOUND]: status.NOT_FOUND,
  [ErrorCode.CURRENCY_RATE_NOT_FOUND]: status.NOT_FOUND,
  [ErrorCode.INCONSISTENT_EXCHANGED_AMOUNT]: status.INVALID_ARGUMENT,
  [ErrorCode.EXPENSE_REFERENCE_NOT_FOUND]: status.INVALID_ARGUMENT,
  [ErrorCode.EXPENSE_ALREADY_REVERTED]: status.ALREADY_EXISTS,
  [ErrorCode.EXPENSE_CORRECTION_CONFLICT]: status.INVALID_ARGUMENT,
  [ErrorCode.VALIDATION_ERROR]: status.INVALID_ARGUMENT,
  [ErrorCode.INTERNAL_ERROR]: status.INTERNAL,
  [ErrorCode.INVALID_TOKEN]: status.UNAUTHENTICATED,
  [ErrorCode.TOKEN_EXPIRED]: status.UNAUTHENTICATED,
  [ErrorCode.IDEMPOTENCY_HASH_MISMATCH]: status.FAILED_PRECONDITION,
};

export const ERROR_CODE_METADATA_KEY = 'error-code';
