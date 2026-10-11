import {Metadata, status} from '@grpc/grpc-js';
import {firstValueFrom} from 'rxjs';

import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  IdempotencyHashMismatchError,
  IdempotencyRequestInProgressError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
  InvalidTokenError,
  TokenExpiredError,
} from '#domain/errors/errors';

import {GrpcBusinessErrorFilter} from '#api/grpc/filters/grpc-business-error.filter';

describe('GrpcBusinessErrorFilter', () => {
  it.each([
    [EventNotFoundError, status.NOT_FOUND, 'B4001', 'Event not found'],
    [EventDeletedError, status.FAILED_PRECONDITION, 'B4002', 'Event is deleted'],
    [InvalidPinCodeError, status.PERMISSION_DENIED, 'B4003', 'Invalid pin code'],
    [CurrencyNotFoundError, status.NOT_FOUND, 'B4004', 'Currency not found'],
    [CurrencyRateNotFoundError, status.NOT_FOUND, 'B4005', 'Currency rate not found'],
    [InvalidTokenError, status.UNAUTHENTICATED, 'B4008', 'Invalid token'],
    [TokenExpiredError, status.UNAUTHENTICATED, 'B4009', 'Token has expired'],
    [
      InconsistentExchangedAmountError,
      status.INVALID_ARGUMENT,
      'B4010',
      'All splitInfo must have exchangedAmount when custom rate is used',
    ],
    [
      IdempotencyHashMismatchError,
      status.FAILED_PRECONDITION,
      'B4011',
      'Idempotency key reused with different request body',
    ],
    [
      IdempotencyRequestInProgressError,
      status.ABORTED,
      'B4016',
      'Another request with this idempotency key is in progress',
    ],
  ])('maps %p to its gRPC status, details and metadata code', async (ErrorClass, grpcStatus, errorCode, details) => {
    const error = await firstValueFrom(new GrpcBusinessErrorFilter().catch(new ErrorClass())).then(
      () => undefined,
      (caught: unknown) => caught as {code: number; details: string; metadata: Metadata},
    );

    expect(error).toMatchObject({code: grpcStatus, details});
    expect(error?.metadata).toBeInstanceOf(Metadata);
    expect(error?.metadata.get('error-code')).toEqual([errorCode]);
  });
});
