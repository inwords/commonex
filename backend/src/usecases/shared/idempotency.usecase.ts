import {Injectable} from '@nestjs/common';
import {createHash} from 'crypto';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IdempotencyKeyValueObject} from '#domain/value-objects/idempotency-key.value-object';
import {IdempotencyHashMismatchError} from '#domain/errors/errors';

@Injectable()
export class IdempotencySharedUseCase {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  async execute<TResult, TBody extends object>(
    key: string | undefined,
    url: string,
    body: TBody,
    fn: () => Promise<TResult>,
  ): Promise<TResult> {
    if (!key) {
      return fn();
    }

    const requestHash = this.computeHash(url, body);
    const [existing] = await this.rDataService.idempotencyKey.findByKey(key);

    if (existing) {
      if (existing.requestHash !== requestHash) {
        throw new IdempotencyHashMismatchError();
      }
      return existing.response as TResult;
    }

    const result = await fn();

    const record = new IdempotencyKeyValueObject({
      key,
      url,
      requestHash,
      response: result as object,
      statusCode: 200,
    }).value;

    await this.rDataService.idempotencyKey.insert(record);

    return result;
  }

  private computeHash(url: string, body: object): string {
    return createHash('sha256').update(JSON.stringify({url, body})).digest('hex');
  }
}
