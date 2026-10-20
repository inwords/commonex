import {createHash} from 'crypto';

import {Injectable} from '@nestjs/common';

import {Result, error, isError, success} from '#packages/result';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IdempotencyHashMismatchError, IdempotencyRequestInProgressError} from '#domain/errors';
import {IDEMPOTENCY_KEY_TTL_MS, IdempotencyKeyValueObject} from '#domain/value-objects/idempotency-key.value-object';

export interface IdempotentInput {
  idempotencyKey?: string | undefined;
}

export const IdempotencyOperation = {
  CREATE_EVENT_V1: 'event.create.v1',
  ADD_USERS_TO_EVENT_V1: 'event.users.add.v1',
  CREATE_EVENT_EXPENSE_V1: 'event.expense.create.v1',
  ADD_USERS_TO_EVENT_V2: 'event.users.add.v2',
  CREATE_EVENT_EXPENSE_V2: 'event.expense.create.v2',
  REVISE_EVENT_EXPENSE_V4: 'event.expense.revise.v4',
  CREATE_EVENT_EXPENSE_V4: 'event.expense.create.v4',
} as const;

export type IdempotencyOperationId = (typeof IdempotencyOperation)[keyof typeof IdempotencyOperation];
export type IdempotencyError = IdempotencyHashMismatchError | IdempotencyRequestInProgressError;

const RESPONSE_VERSION = 1;
const DATE_TYPE = 'commonex.date';

interface EncodedDate {
  type: typeof DATE_TYPE;
  value: string;
}
const normalizeForJson = (value: unknown): unknown => {
  if (value instanceof Date) {
    return value.toISOString();
  }
  if (Array.isArray(value)) {
    return value.map((item) => normalizeForJson(item));
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, item]) => item !== undefined)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, item]) => [key, normalizeForJson(item)]),
    );
  }
  return value;
};

const encodeResponse = (value: unknown): unknown => {
  if (value instanceof Date) {
    return {type: DATE_TYPE, value: value.toISOString()} satisfies EncodedDate;
  }
  if (Array.isArray(value)) {
    return value.map((item) => encodeResponse(item));
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, encodeResponse(item)]));
  }
  return value;
};

const decodeResponse = (value: unknown): unknown => {
  if (
    value !== null &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    (value as Partial<EncodedDate>).type === DATE_TYPE &&
    typeof (value as Partial<EncodedDate>).value === 'string'
  ) {
    return new Date((value as EncodedDate).value);
  }
  if (Array.isArray(value)) {
    return value.map((item) => decodeResponse(item));
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, decodeResponse(item)]));
  }
  return value;
};

@Injectable()
export class IdempotencySharedUseCase {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  async execute<TValue extends object, TError>(
    key: string | undefined,
    operationId: IdempotencyOperationId,
    body: object,
    fn: (trx: ITransaction) => Promise<Result<TValue, TError>>,
  ): Promise<Result<TValue, TError | IdempotencyError>> {
    return this.rDataService.transaction(async (trx) => {
      if (!key) {
        return fn(trx);
      }
      const requestHash = this.computeHash(operationId, body);
      const replay = await this.findReplay(key, operationId, requestHash, false, trx);
      if (replay !== null) {
        return replay as Result<TValue, IdempotencyError>;
      }

      const [acquired] = await this.rDataService.idempotencyKey.tryAcquireLock(key, trx);
      if (!acquired) {
        return error(new IdempotencyRequestInProgressError());
      }

      const replayAfterLock = await this.findReplay(key, operationId, requestHash, true, trx);
      if (replayAfterLock !== null) {
        return replayAfterLock as Result<TValue, IdempotencyError>;
      }

      const result = await fn(trx);
      if (isError(result)) {
        return result;
      }

      const response = encodeResponse(result.value);
      if (response === null || typeof response !== 'object') {
        throw new Error('Idempotency responses must be objects');
      }

      const record = new IdempotencyKeyValueObject({
        key,
        operationId,
        requestHash,
        response,
        responseVersion: RESPONSE_VERSION,
      }).value;

      await this.rDataService.idempotencyKey.insert(record, trx);

      return result;
    });
  }

  private async findReplay(
    key: string,
    operationId: IdempotencyOperationId,
    requestHash: string,
    deleteExpired: boolean,
    trx: ITransaction,
  ): Promise<Result<object, IdempotencyHashMismatchError> | null> {
    const [existing] = await this.rDataService.idempotencyKey.findByKey(key, trx);
    if (!existing) {
      return null;
    }

    const effectiveExpiresAt = Math.min(
      existing.expiresAt.getTime(),
      existing.createdAt.getTime() + IDEMPOTENCY_KEY_TTL_MS,
    );
    if (effectiveExpiresAt <= Date.now()) {
      if (deleteExpired) {
        await this.rDataService.idempotencyKey.deleteByKey(key, trx);
      }
      return null;
    }

    if (existing.operationId !== operationId || existing.requestHash !== requestHash) {
      return error(new IdempotencyHashMismatchError());
    }
    if (existing.responseVersion !== RESPONSE_VERSION) {
      throw new Error(`Unsupported idempotency response version: ${existing.responseVersion}`);
    }

    return success(decodeResponse(existing.response) as object);
  }

  private computeHash(operationId: IdempotencyOperationId, body: object): string {
    const canonicalRequest = JSON.stringify(normalizeForJson({operationId, body}));
    return createHash('sha256').update(canonicalRequest).digest('hex');
  }
}
