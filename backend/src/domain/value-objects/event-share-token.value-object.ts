import {randomBytes} from 'crypto';

import {PartialByKeys} from '#packages/types';

import {IEventShareToken} from '#domain/entities/event-share-token.entity';
import {ValueObject} from '#domain/value-objects/value-object';

const TOKEN_LENGTH_BYTES = 32;
const DEFAULT_EXPIRATION_DAYS = 14;

export type TEventShareTokenDefaultKeys = keyof Pick<IEventShareToken, 'token' | 'expiresAt' | 'createdAt'>;

export const eventShareTokenDefaultValues: {
  [K in TEventShareTokenDefaultKeys]: () => IEventShareToken[K];
} = {
  token: () => randomBytes(TOKEN_LENGTH_BYTES).toString('hex'),
  expiresAt: () => {
    const date = new Date();
    date.setDate(date.getDate() + DEFAULT_EXPIRATION_DAYS);
    return date;
  },
  createdAt: () => new Date(),
} as const;
Object.freeze(eventShareTokenDefaultValues);

export class EventShareTokenValueObject extends ValueObject<IEventShareToken> {
  public override value: IEventShareToken;

  constructor(objectValues: PartialByKeys<IEventShareToken, TEventShareTokenDefaultKeys>) {
    const valueObject: IEventShareToken = {
      ...objectValues,
      token: ValueObject.getValueOrDefault(objectValues.token, eventShareTokenDefaultValues.token),
      expiresAt: ValueObject.getValueOrDefault(objectValues.expiresAt, eventShareTokenDefaultValues.expiresAt),
      createdAt: ValueObject.getValueOrDefault(objectValues.createdAt, eventShareTokenDefaultValues.createdAt),
    };
    super(valueObject);
    this.value = valueObject;
  }
}
