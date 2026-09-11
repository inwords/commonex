import {Column, Entity, PrimaryColumn} from 'typeorm';

import {type ICurrency} from '#domain/entities/currency.entity';

@Entity({name: 'currency'})
export class CurrencyEntity implements ICurrency {
  @PrimaryColumn({type: 'varchar'})
  id!: ICurrency['id'];

  @Column({type: 'varchar'})
  code!: ICurrency['code'];

  @Column({type: 'timestamptz'})
  createdAt!: ICurrency['createdAt'];

  @Column({type: 'timestamptz'})
  updatedAt!: ICurrency['updatedAt'];
}
