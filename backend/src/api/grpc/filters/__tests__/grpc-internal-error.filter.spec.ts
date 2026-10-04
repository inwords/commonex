import {Metadata, status} from '@grpc/grpc-js';
import {ArgumentsHost, Logger} from '@nestjs/common';
import {firstValueFrom} from 'rxjs';

import {GrpcInternalErrorFilter} from '#api/grpc/filters/grpc-internal-error.filter';

describe('GrpcInternalErrorFilter', () => {
  it('returns a safe INTERNAL error with metadata and logs the source exception once', async () => {
    const loggerError = jest.spyOn(Logger.prototype, 'error').mockImplementation();
    const exception = new Error('duplicate key value violates constraint', {cause: new Error('driver details')});
    const host = {
      getArgByIndex: (index: number): unknown =>
        [undefined, new Metadata(), {getPath: () => '/user.UserService/GetEventInfo'}][index],
    } as ArgumentsHost;

    let thrown: unknown;
    try {
      await firstValueFrom(new GrpcInternalErrorFilter().catch(exception, host));
    } catch (error) {
      thrown = error;
    }

    expect(thrown).toMatchObject({
      code: status.INTERNAL,
      details: 'Internal server error',
      metadata: expect.any(Metadata),
    });
    expect((thrown as {metadata: Metadata}).metadata.get('error-code')).toEqual(['B4007']);
    expect(loggerError).toHaveBeenCalledTimes(1);
    expect(loggerError).toHaveBeenCalledWith(
      expect.objectContaining({
        transport: 'grpc',
        operation: '/user.UserService/GetEventInfo',
        exception,
        stack: exception.stack,
        cause: exception.cause,
      }),
    );
  });
});
