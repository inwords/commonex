import {ulid} from 'ulid';

import {PartialByKeys} from '#packages/types';

import {IExpense} from '#domain/entities/expense.entity';
import {ValueObject} from '#domain/value-objects/value-object';

export type TExpenseDefaultKeys = keyof Pick<IExpense, 'id' | 'version' | 'createdAt' | 'updatedAt'>;

export const expenseDefaultValues: {
  [K in TExpenseDefaultKeys]: () => IExpense[K];
} = {
  id: () => ulid(),
  version: () => 1,
  createdAt: () => new Date(),
  updatedAt: () => new Date(),
} as const;
Object.freeze(expenseDefaultValues);

export class ExpenseValueObject extends ValueObject<IExpense> {
  public override value: IExpense;

  constructor(objectValues: PartialByKeys<IExpense, TExpenseDefaultKeys>) {
    const valueObject: IExpense = {
      // Class-validator устанавливает значение `undefined` для необязательных полей,
      // если они не переданы. Чтобы избежать перезаписи значений по-умолчанию на `undefined`,
      // сначала выполняется деструктуризация объекта requitedObjectValues.
      ...objectValues,
      id: ValueObject.getValueOrDefault(objectValues.id, expenseDefaultValues.id),
      version: ValueObject.getValueOrDefault(objectValues.version, expenseDefaultValues.version),
      createdAt: ValueObject.getValueOrDefault(objectValues.createdAt, expenseDefaultValues.createdAt),
      updatedAt: ValueObject.getValueOrDefault(objectValues.updatedAt, expenseDefaultValues.updatedAt),
    };
    super(valueObject);
    this.value = valueObject;
  }
}
