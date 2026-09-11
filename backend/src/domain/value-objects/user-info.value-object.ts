import {ulid} from 'ulid';

import {PartialByKeys} from '#packages/types';

import {IUserInfo} from '#domain/entities/user-info.entity';
import {ValueObject} from '#domain/value-objects/value-object';

export type TUserInfoDefaultKeys = keyof Pick<IUserInfo, 'id' | 'createdAt' | 'updatedAt'>;

export const userInfoDefaultValues: {
  [K in TUserInfoDefaultKeys]: () => IUserInfo[K];
} = {
  id: () => ulid(),
  createdAt: () => new Date(),
  updatedAt: () => new Date(),
} as const;
Object.freeze(userInfoDefaultValues);

export class UserInfoValueObject extends ValueObject<IUserInfo> {
  public override value: IUserInfo;

  constructor(objectValues: PartialByKeys<IUserInfo, TUserInfoDefaultKeys>) {
    const valueObject: IUserInfo = {
      // Class-validator устанавливает значение `undefined` для необязательных полей,
      // если они не переданы. Чтобы избежать перезаписи значений по-умолчанию на `undefined`,
      // сначала выполняется деструктуризация объекта requitedObjectValues.
      ...objectValues,
      id: ValueObject.getValueOrDefault(objectValues.id, userInfoDefaultValues.id),
      createdAt: ValueObject.getValueOrDefault(objectValues.createdAt, userInfoDefaultValues.createdAt),
      updatedAt: ValueObject.getValueOrDefault(objectValues.updatedAt, userInfoDefaultValues.updatedAt),
    };
    super(valueObject);
    this.value = valueObject;
  }
}
