import {ValueObject} from '#domain/value-objects/value-object';
import {PartialByKeys} from '#packages/types';
import {IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

const TTL_MS = 24 * 60 * 60 * 1000;

export type TIdempotencyKeyDefaultKeys = keyof Pick<IIdempotencyKey, 'createdAt' | 'expiresAt'>;

export const idempotencyKeyDefaultValues: {
  [K in TIdempotencyKeyDefaultKeys]: () => IIdempotencyKey[K];
} = {
  createdAt: () => new Date(),
  expiresAt: () => new Date(Date.now() + TTL_MS),
} as const;
Object.freeze(idempotencyKeyDefaultValues);

export class IdempotencyKeyValueObject extends ValueObject<IIdempotencyKey> {
  public override value: IIdempotencyKey;

  constructor(objectValues: PartialByKeys<IIdempotencyKey, TIdempotencyKeyDefaultKeys>) {
    const valueObject: IIdempotencyKey = {
      ...objectValues,
      createdAt: ValueObject.getValueOrDefault(objectValues.createdAt, idempotencyKeyDefaultValues.createdAt),
      expiresAt: ValueObject.getValueOrDefault(objectValues.expiresAt, idempotencyKeyDefaultValues.expiresAt),
    };
    super(valueObject);
    this.value = valueObject;
  }
}
