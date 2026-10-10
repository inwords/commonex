import {Injectable} from '@nestjs/common';

import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

import {CleanupResult} from './cleanup-result';
import {deleteExpiredInBatches} from './delete-expired-in-batches';

type Output =
  | {acquired: false}
  | {
      acquired: true;
      result: {
        idempotencyKeys: CleanupResult;
        eventShareTokens: CleanupResult;
      };
    };

@Injectable()
export class CleanupExpiredDataUseCase implements UseCase<void, Output> {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  public async execute(): Promise<Output> {
    return this.rDataService.transaction<Output>(async (trx) => {
      if (!(await this.rDataService.tryAcquireTransactionLock('commonex:cron:data-retention', trx))) {
        return {acquired: false};
      }

      const idempotencyKeys = await deleteExpiredInBatches((input) =>
        this.rDataService.idempotencyKey.deleteExpiredBatch(input, trx),
      );
      const eventShareTokens = await deleteExpiredInBatches((input) =>
        this.rDataService.eventShareToken.deleteExpiredBatch(input, trx),
      );

      return {acquired: true, result: {idempotencyKeys, eventShareTokens}};
    });
  }
}
