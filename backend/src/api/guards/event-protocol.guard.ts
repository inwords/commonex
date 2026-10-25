import {Metadata} from '@grpc/grpc-js';
import {CanActivate, ExecutionContext, Injectable} from '@nestjs/common';

import {EventProtocolVersion} from '#domain/entities/event-protocol-version.enum';

import {CheckEventProtocolUseCase} from '#usecases/shared/check-event-protocol.usecase';

import {unwrapResult} from '#api/unwrap-result';

interface EventProtocolHttpRequest {
  headers: Record<string, unknown>;
  params?: unknown;
  body?: unknown;
}

const readObject = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};

const readString = (value: unknown): string | undefined => (typeof value === 'string' ? value : undefined);

@Injectable()
export class EventProtocolGuard implements CanActivate {
  constructor(private readonly checkEventProtocolUseCase: CheckEventProtocolUseCase) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (context.getType() === 'http') {
      const request = context.switchToHttp().getRequest<EventProtocolHttpRequest>();
      return this.check(
        readString(request.headers['user-agent']),
        readString(request.headers['x-commonex-protocol-version']),
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
        readString(metadata.get('x-commonex-protocol-version')[0]),
        payload['eventId'],
        payload,
      );
    }
    return true;
  }

  private async check(
    userAgent: string | undefined,
    protocolHeader: string | undefined,
    eventId: unknown,
    payload: Record<string, unknown>,
  ): Promise<boolean> {
    if (!/(?:^|\s)CommonEx\/\d+ \((?:Android|iOS)\/[rd]\)(?:\s|$)/.test(userAgent ?? '')) {
      return true;
    }
    const parsedVersion = protocolHeader && /^[1-9]\d*$/.test(protocolHeader) ? Number(protocolHeader) : NaN;
    const clientProtocolVersion = Number.isSafeInteger(parsedVersion) ? parsedVersion : EventProtocolVersion.Legacy;
    const requestedProtocolVersion =
      payload['version'] === EventProtocolVersion.Legacy || payload['version'] === EventProtocolVersion.ExpenseEditing
        ? payload['version']
        : undefined;
    return unwrapResult(
      await this.checkEventProtocolUseCase.execute({
        ...(typeof eventId === 'string' ? {eventId} : {}),
        ...(requestedProtocolVersion !== undefined ? {requestedProtocolVersion} : {}),
        clientProtocolVersion,
      }),
    );
  }
}
