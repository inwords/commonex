import {Metadata, ServiceClientConstructor, ServiceError, credentials, loadPackageDefinition} from '@grpc/grpc-js';
import {loadSync} from '@grpc/proto-loader';

import {PROTO_PATH} from './test-app';

export type UserServiceMethod =
  | 'CreateEvent'
  | 'GetEventInfo'
  | 'DeleteEvent'
  | 'AddUsersToEvent'
  | 'GetAllEventExpenses'
  | 'CreateExpense'
  | 'GetEventInfoV2'
  | 'AddUsersToEventV2'
  | 'GetAllEventExpensesV2'
  | 'CreateExpenseV2'
  | 'CreateEventShareTokenV2';

type UnaryMethod = (
  request: object,
  metadata: Metadata,
  callback: (error: ServiceError | null, response: unknown) => void,
) => void;

export type UserServiceClient = Record<UserServiceMethod, UnaryMethod> & {close: () => void};

export const createUserServiceClient = (url: string): UserServiceClient => {
  const definition = loadSync(PROTO_PATH, {
    keepCase: false,
    longs: String,
    enums: String,
    defaults: false,
    arrays: true,
    objects: true,
    // Mirrors the server loader options: synthetic oneofs of proto3 optional fields would add `_exchangedAmount`-style
    // keys to decoded responses.
    oneofs: false,
  });
  const proto = loadPackageDefinition(definition) as unknown as {user: {UserService: ServiceClientConstructor}};

  return new proto.user.UserService(url, credentials.createInsecure()) as unknown as UserServiceClient;
};

export const callUnary = <TResponse>(
  client: UserServiceClient,
  method: UserServiceMethod,
  request: object,
  metadata: Metadata = new Metadata(),
): Promise<TResponse> =>
  new Promise((resolve, reject) => {
    client[method](request, metadata, (error, response) => {
      if (error) {
        reject(error);
        return;
      }

      resolve(response as TResponse);
    });
  });

export const expectGrpcError = async (
  call: Promise<unknown>,
): Promise<{code: number; details: string; errorCode: string | undefined}> => {
  try {
    await call;
  } catch (error) {
    const serviceError = error as ServiceError;
    const errorCode = serviceError.metadata.get('error-code')[0];

    return {
      code: serviceError.code,
      details: serviceError.details,
      errorCode: typeof errorCode === 'string' ? errorCode : undefined,
    };
  }

  throw new Error('Expected the gRPC call to fail');
};
