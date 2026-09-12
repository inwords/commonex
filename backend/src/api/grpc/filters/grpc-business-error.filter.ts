import {Metadata} from '@grpc/grpc-js';
import {Catch, RpcExceptionFilter} from '@nestjs/common';
import {Observable, throwError} from 'rxjs';

import {ErrorCode} from '#domain/errors/error-codes.enum';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  EventOperationConflictError,
  IdempotencyHashMismatchError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

import {ERROR_CODE_METADATA_KEY, GRPC_STATUS_BY_ERROR_CODE} from './grpc-status.map';

interface BusinessError {
  code: ErrorCode;
  message: string;
}

@Catch(
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
)
export class GrpcBusinessErrorFilter implements RpcExceptionFilter<BusinessError> {
  catch(exception: BusinessError): Observable<never> {
    const metadata = new Metadata();
    metadata.set(ERROR_CODE_METADATA_KEY, exception.code);

    return throwError(() => ({
      code: GRPC_STATUS_BY_ERROR_CODE[exception.code],
      details: exception.message,
      metadata,
    }));
  }
}
