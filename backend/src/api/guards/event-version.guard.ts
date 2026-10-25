import {Metadata} from '@grpc/grpc-js';
import {CanActivate, ExecutionContext, Injectable} from '@nestjs/common';

import {CheckEventVersionSharedUseCase} from '#usecases/shared/check-event-version.usecase';

import {unwrapResult} from '#api/unwrap-result';

interface EventVersionHttpRequest {
  headers: Record<string, unknown>;
  params?: unknown;
  body?: unknown;
}

const readObject = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};

const readString = (value: unknown): string | undefined => (typeof value === 'string' ? value : undefined);

@Injectable()
export class EventVersionGuard implements CanActivate {
  constructor(private readonly checkEventVersionUseCase: CheckEventVersionSharedUseCase) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (context.getType() === 'http') {
      const request = context.switchToHttp().getRequest<EventVersionHttpRequest>();
      return this.check(
        readString(request.headers['user-agent']),
        readString(request.headers['x-commonex-version']),
        readObject(request.params)['eventId'],
        readObject(request.body),
      );
    }
    if (context.getType() === 'rpc') {
      const metadata = context.switchToRpc().getContext<unknown>();
      if (!(metadata instanceof Metadata)) {
        throw new Error('Expected gRPC Metadata as the handler context');
      }
      const payload = readObject(context.switchToRpc().getData<unknown>());
      return this.check(
        readString(metadata.get('user-agent')[0]),
        readString(metadata.get('x-commonex-version')[0]),
        payload['eventId'],
        payload,
      );
    }
    return true;
  }

  private async check(
    userAgent: string | undefined,
    versionHeader: string | undefined,
    eventId: unknown,
    payload: Record<string, unknown>,
  ): Promise<boolean> {
    return unwrapResult(
      await this.checkEventVersionUseCase.execute({
        eventId,
        requestedVersion: payload['version'],
        userAgent,
        clientVersion: versionHeader,
      }),
    );
  }
}
