import {PartialByKeys} from '#packages/types';

import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {ValueObject} from '#domain/value-objects/value-object';

export type TCurrencyRateDefaultKeys = keyof Pick<ICurrencyRate, 'createdAt' | 'updatedAt'>;

export const currencyRateDefaultValues: {
  [K in TCurrencyRateDefaultKeys]: () => ICurrencyRate[K];
} = {
  createdAt: () => new Date(),
  updatedAt: () => new Date(),
} as const;
Object.freeze(currencyRateDefaultValues);

export class CurrencyRateValueObject extends ValueObject<ICurrencyRate> {
  public override value: ICurrencyRate;

  constructor(objectValues: PartialByKeys<ICurrencyRate, TCurrencyRateDefaultKeys>) {
    const valueObject: ICurrencyRate = {
      // Class-validator устанавливает значение `undefined` для необязательных полей,
      // если они не переданы. Чтобы избежать перезаписи значений по-умолчанию на `undefined`,
      // сначала выполняется деструктуризация объекта requitedObjectValues.
      ...objectValues,
      createdAt: ValueObject.getValueOrDefault(objectValues.createdAt, currencyRateDefaultValues.createdAt),
      updatedAt: ValueObject.getValueOrDefault(objectValues.updatedAt, currencyRateDefaultValues.updatedAt),
    };
    super(valueObject);
    this.value = valueObject;
  }
}
