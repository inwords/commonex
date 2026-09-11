import {Column, Entity, PrimaryColumn} from 'typeorm';

import {type IEvent} from '#domain/entities/event.entity';

@Entity({name: 'event'})
export class EventEntity implements IEvent {
  @PrimaryColumn({type: 'varchar'})
  id!: IEvent['id'];

  @Column({type: 'varchar'})
  name!: IEvent['name'];

  @Column({type: 'varchar'})
  currencyId!: IEvent['currencyId'];

  @Column({type: 'varchar'})
  pinCode!: IEvent['pinCode'];

  @Column({type: 'timestamptz'})
  createdAt!: IEvent['createdAt'];

  @Column({type: 'timestamptz'})
  updatedAt!: IEvent['updatedAt'];

  @Column({type: 'timestamptz', nullable: true})
  deletedAt!: IEvent['deletedAt'];
}
