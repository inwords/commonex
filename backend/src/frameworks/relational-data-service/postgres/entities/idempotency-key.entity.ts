import {Column, Entity, Index, PrimaryColumn} from 'typeorm';
import {type IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

@Entity('idempotency_keys')
export class IdempotencyKeyEntity implements IIdempotencyKey {
  @PrimaryColumn({type: 'varchar'})
  key!: IIdempotencyKey['key'];

  @Column({type: 'varchar'})
  url!: IIdempotencyKey['url'];

  @Column({type: 'varchar'})
  requestHash!: IIdempotencyKey['requestHash'];

  @Column({type: 'jsonb'})
  response!: IIdempotencyKey['response'];

  @Column({type: 'integer'})
  statusCode!: IIdempotencyKey['statusCode'];

  @Index()
  @Column({type: 'timestamptz'})
  expiresAt!: IIdempotencyKey['expiresAt'];

  @Column({type: 'timestamptz'})
  createdAt!: IIdempotencyKey['createdAt'];
}
