import {Injectable} from '@nestjs/common';

import {Result, error, success} from '#packages/result';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventVersion} from '#domain/entities/event-version.enum';
import {ClientUpdateRequiredError} from '#domain/errors/errors';

interface Input {
  eventId?: unknown;
  requestedVersion?: unknown;
  userAgent?: string | undefined;
  clientVersion?: string | undefined;
}

@Injectable()
export class CheckEventVersionSharedUseCase {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  async execute(input: Input): Promise<Result<boolean, ClientUpdateRequiredError>> {
    if (!/(?:^|\s)CommonEx\/\d+ \((?:Android|iOS)\/[rd]\)(?:\s|$)/.test(input.userAgent ?? '')) {
      return success(true);
    }
    const parsedVersion =
      input.clientVersion && /^[1-9]\d*$/.test(input.clientVersion) ? Number(input.clientVersion) : NaN;
    const clientVersion = Number.isSafeInteger(parsedVersion) ? parsedVersion : EventVersion.Legacy;
    let requiredVersion: number =
      input.requestedVersion === EventVersion.ExpenseEditing ? EventVersion.ExpenseEditing : EventVersion.Legacy;

    if (typeof input.eventId === 'string' && input.eventId) {
      const [event] = await this.rDataService.event.findById(input.eventId);
      if (!event) {
        return success(true);
      }
      if (event.deletedAt !== null) {
        return success(true);
      }
      requiredVersion = event.version;
    }

    return requiredVersion > clientVersion ? error(new ClientUpdateRequiredError(requiredVersion)) : success(true);
  }
}
