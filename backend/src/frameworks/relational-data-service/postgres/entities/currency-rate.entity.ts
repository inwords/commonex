import {Column, Entity, PrimaryColumn} from 'typeorm';

import {type DateWithoutTime} from '#packages/date-utils';

import {type ICurrencyRate} from '#domain/entities/currency-rate.entity';

@Entity('currency_rate')
export class CurrencyRateEntity implements ICurrencyRate {
  @PrimaryColumn({
    type: 'date',
  })
  date!: DateWithoutTime; //2024-12-31

  @Column({type: 'jsonb'})
  rate!: Record<string, number>;

  @Column({type: 'timestamptz'})
  createdAt!: ICurrencyRate['createdAt'];

  @Column({type: 'timestamptz'})
  updatedAt!: ICurrencyRate['updatedAt'];
}
