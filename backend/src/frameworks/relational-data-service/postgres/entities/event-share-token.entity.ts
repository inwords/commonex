import {Column, Entity, Index, PrimaryColumn} from 'typeorm';

import {type IEventShareToken} from '#domain/entities/event-share-token.entity';

@Entity({name: 'event_share_token'})
@Index(['eventId'])
export class EventShareTokenEntity implements IEventShareToken {
  @PrimaryColumn({type: 'varchar', length: 64})
  token!: IEventShareToken['token'];

  @Column({type: 'varchar'})
  eventId!: IEventShareToken['eventId'];

  @Column({type: 'timestamptz'})
  expiresAt!: IEventShareToken['expiresAt'];

  @Column({type: 'timestamptz'})
  createdAt!: IEventShareToken['createdAt'];
}
