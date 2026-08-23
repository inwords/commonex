import {ArgumentsHost, HttpStatus} from '@nestjs/common';
import {FILTER_CATCH_EXCEPTIONS} from '@nestjs/common/constants';
import {AbstractHttpAdapter} from '@nestjs/core';

import {BusinessErrorFilter} from '#api/http/filters/business-error.filter';
import {EventOperationConflictError} from '#domain/errors/errors';

describe('BusinessErrorFilter', () => {
  it('should return the normalized conflict response for concurrent event operations', () => {
    const reply = jest.fn();
    const response = {};
    const httpAdapter = {reply} as unknown as AbstractHttpAdapter;
    const host = {
      switchToHttp: () => ({
        getResponse: (): object => response,
      }),
    } as ArgumentsHost;
    const exception = new EventOperationConflictError();

    new BusinessErrorFilter(httpAdapter).catch(exception, host);

    expect(Reflect.getMetadata(FILTER_CATCH_EXCEPTIONS, BusinessErrorFilter)).toContain(EventOperationConflictError);
    expect(reply).toHaveBeenCalledWith(
      response,
      {
        statusCode: HttpStatus.CONFLICT,
        code: 'B4015',
        message: 'Another operation is in progress for this event',
      },
      HttpStatus.CONFLICT,
    );
  });
});
