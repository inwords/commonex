import {Injectable} from '@nestjs/common';

import {Result, error, success} from '#packages/result';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventProtocolVersion} from '#domain/entities/event-protocol-version.enum';
import {ClientUpdateRequiredError} from '#domain/errors/errors';

interface Input {
  eventId?: string;
  requestedProtocolVersion?: EventProtocolVersion;
  clientProtocolVersion: number;
}

@Injectable()
export class CheckEventProtocolUseCase {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  async execute(input: Input): Promise<Result<boolean, ClientUpdateRequiredError>> {
    let requiredProtocolVersion: number = input.requestedProtocolVersion ?? EventProtocolVersion.Legacy;

    if (input.eventId) {
      const [event] = await this.rDataService.event.findById(input.eventId);
      if (!event) {
        return success(true);
      }
      if (event.deletedAt !== null) {
        return success(true);
      }
      requiredProtocolVersion = event.version;
    }

    return requiredProtocolVersion > input.clientProtocolVersion
      ? error(new ClientUpdateRequiredError(requiredProtocolVersion))
      : success(true);
  }
}
