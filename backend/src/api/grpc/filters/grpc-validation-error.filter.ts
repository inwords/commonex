import {Metadata, status} from '@grpc/grpc-js';
import {BadRequestException, Catch, RpcExceptionFilter} from '@nestjs/common';
import {Observable, throwError} from 'rxjs';

import {ErrorCode} from '#domain/errors/error-codes.enum';

import {ERROR_CODE_METADATA_KEY} from './grpc-status.map';

@Catch(BadRequestException)
export class GrpcValidationErrorFilter implements RpcExceptionFilter<BadRequestException> {
  catch(exception: BadRequestException): Observable<never> {
    const {message} = exception.getResponse() as {message: unknown};
    const details = Array.isArray(message) ? message.join('; ') : String(message);
    const metadata = new Metadata();
    metadata.set(ERROR_CODE_METADATA_KEY, ErrorCode.VALIDATION_ERROR);

    return throwError(() => ({code: status.INVALID_ARGUMENT, details, metadata}));
  }
}
