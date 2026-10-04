import {ArgumentsHost, HttpStatus, Logger, UnauthorizedException} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';

import {InternalErrorFilter} from '#api/http/filters/internal-error.filter';

const createHost = (): {host: ArgumentsHost; response: object} => {
  const request = {id: 'request-id', method: 'GET', url: '/user/event/example?pinCode=1234'};
  const response = {};

  return {
    host: {
      getArgByIndex: (index: number): unknown => [request, response][index],
      switchToHttp: () => ({
        getRequest: (): object => request,
        getResponse: (): object => response,
      }),
    } as ArgumentsHost,
    response,
  };
};

describe('InternalErrorFilter', () => {
  it('returns a safe internal error and logs the source exception once', () => {
    const reply = jest.fn();
    const loggerError = jest.spyOn(Logger.prototype, 'error').mockImplementation();
    const {host, response} = createHost();
    const exception = new Error('SELECT secret FROM users', {cause: new Error('driver details')});

    new InternalErrorFilter({reply} as unknown as AbstractHttpAdapter).catch(exception, host);

    expect(reply).toHaveBeenCalledWith(
      response,
      {statusCode: HttpStatus.INTERNAL_SERVER_ERROR, code: 'B4007', message: 'Internal server error'},
      HttpStatus.INTERNAL_SERVER_ERROR,
    );
    expect(JSON.stringify(reply.mock.calls)).not.toContain('SELECT secret');
    expect(loggerError).toHaveBeenCalledTimes(1);
    expect(loggerError).toHaveBeenCalledWith(
      expect.objectContaining({
        transport: 'http',
        operation: 'GET /user/event/example?pinCode=1234',
        requestId: 'request-id',
        exception,
        stack: exception.stack,
        cause: exception.cause,
      }),
    );
  });

  it('preserves explicit HTTP exceptions without logging them as internal errors', () => {
    const reply = jest.fn();
    const loggerError = jest.spyOn(Logger.prototype, 'error').mockImplementation();
    const {host, response} = createHost();
    const adapter = {
      end: jest.fn(),
      isHeadersSent: jest.fn(() => false),
      reply,
    } as unknown as AbstractHttpAdapter;

    new InternalErrorFilter(adapter).catch(new UnauthorizedException('Invalid devtools secret'), host);

    expect(reply).toHaveBeenCalledWith(
      response,
      {statusCode: HttpStatus.UNAUTHORIZED, message: 'Invalid devtools secret', error: 'Unauthorized'},
      HttpStatus.UNAUTHORIZED,
    );
    expect(loggerError).not.toHaveBeenCalled();
  });
});
