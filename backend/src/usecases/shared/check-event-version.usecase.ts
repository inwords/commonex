import {Injectable} from '@nestjs/common';

import {Result, error, success} from '#packages/result';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventVersion} from '#domain/entities/event-version.enum';
import {ClientUpdateRequiredError} from '#domain/errors/errors';

import {getMobileEventVersion} from './get-mobile-event-version';

interface Input {
  eventId?: unknown;
  requestedVersion?: unknown;
  userAgent?: string | undefined;
}

@Injectable()
export class CheckEventVersionSharedUseCase {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  async execute(input: Input): Promise<Result<boolean, ClientUpdateRequiredError>> {
    const clientVersion: number | undefined = getMobileEventVersion(input.userAgent);
    if (clientVersion === undefined) {
      return success(true);
    }
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
