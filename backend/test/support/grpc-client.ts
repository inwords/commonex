import {Metadata, ServiceClientConstructor, ServiceError, credentials, loadPackageDefinition} from '@grpc/grpc-js';
import {loadSync} from '@grpc/proto-loader';

import {GRPC_PROTO_LOADER_OPTIONS} from '../../src/app.factory';
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
  const definition = loadSync(PROTO_PATH, GRPC_PROTO_LOADER_OPTIONS);
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
    const maybeServiceError = error as {code?: unknown; metadata?: unknown};

    if (typeof maybeServiceError.code !== 'number' || !(maybeServiceError.metadata instanceof Metadata)) {
      throw error;
    }

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
