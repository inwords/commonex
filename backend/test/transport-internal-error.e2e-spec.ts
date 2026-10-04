import {status} from '@grpc/grpc-js';
import {Logger} from '@nestjs/common';

import {GetEventInfoUseCase} from '#usecases/users/get-event-info.usecase';

import {UserServiceClient, callUnary, createUserServiceClient, expectGrpcError} from './support/grpc-client';
import {TestApp, createTestApp} from './support/test-app';

describe('transport internal errors', () => {
  let testApp: TestApp;
  let client: UserServiceClient;

  const cause = new Error('password=postgres');
  const exception = new Error('SELECT * FROM private_table WHERE token = $1', {cause});
  const execute = jest.fn(() => Promise.reject(exception));

  beforeAll(async () => {
    testApp = await createTestApp({
      configure: (builder) => builder.overrideProvider(GetEventInfoUseCase).useValue({execute}),
      grpc: true,
    });

    if (testApp.grpcUrl === null) {
      throw new Error('The test app did not start a gRPC microservice');
    }

    client = createUserServiceClient(testApp.grpcUrl);
  });

  afterAll(async () => {
    client.close();
    await testApp.close();
  });

  it('sanitizes an unknown HTTP exception and logs it once', async () => {
    const loggerError = jest.spyOn(Logger.prototype, 'error').mockImplementation();

    const response = await testApp.app.inject({method: 'GET', url: '/user/event/example?pinCode=1234'});

    expect(response.statusCode).toBe(500);
    expect(response.json()).toEqual({statusCode: 500, code: 'B4007', message: 'Internal server error'});
    expect(response.body).not.toContain(exception.message);
    expect(response.body).not.toContain(cause.message);
    expect(loggerError).toHaveBeenCalledTimes(1);
    expect(loggerError).toHaveBeenCalledWith(expect.objectContaining({transport: 'http', exception}));
  });

  it('sanitizes an unknown gRPC exception and logs it once', async () => {
    const loggerError = jest.spyOn(Logger.prototype, 'error').mockImplementation();

    const error = await expectGrpcError(callUnary(client, 'GetEventInfo', {eventId: 'example', pinCode: '1234'}));

    expect(error).toEqual({code: status.INTERNAL, details: 'Internal server error', errorCode: 'B4007'});
    expect(error.details).not.toContain(exception.message);
    expect(error.details).not.toContain(cause.message);
    expect(loggerError).toHaveBeenCalledTimes(1);
    expect(loggerError).toHaveBeenCalledWith(expect.objectContaining({transport: 'grpc', exception}));
  });
});
