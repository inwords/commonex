import {ulid} from 'ulid';

import {PartialByKeys} from '#packages/types';

import {ICurrency} from '#domain/entities/currency.entity';
import {ValueObject} from '#domain/value-objects/value-object';

export type TCurrencyDefaultKeys = keyof Pick<ICurrency, 'id' | 'createdAt' | 'updatedAt'>;

export const currencyDefaultValues: {
  [K in TCurrencyDefaultKeys]: () => ICurrency[K];
} = {
  id: () => ulid(),
  createdAt: () => new Date(),
  updatedAt: () => new Date(),
} as const;
Object.freeze(currencyDefaultValues);

export class CurrencyValueObject extends ValueObject<ICurrency> {
  public override value: ICurrency;

  constructor(objectValues: PartialByKeys<ICurrency, TCurrencyDefaultKeys>) {
    const valueObject: ICurrency = {
      // Class-validator устанавливает значение `undefined` для необязательных полей,
      // если они не переданы. Чтобы избежать перезаписи значений по-умолчанию на `undefined`,
      // сначала выполняется деструктуризация объекта requitedObjectValues.
      ...objectValues,
      id: ValueObject.getValueOrDefault(objectValues.id, currencyDefaultValues.id),
      createdAt: ValueObject.getValueOrDefault(objectValues.createdAt, currencyDefaultValues.createdAt),
      updatedAt: ValueObject.getValueOrDefault(objectValues.updatedAt, currencyDefaultValues.updatedAt),
    };
    super(valueObject);
    this.value = valueObject;
  }
}
