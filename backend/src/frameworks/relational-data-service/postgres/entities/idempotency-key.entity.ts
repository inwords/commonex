import {Column, Entity, Index, PrimaryColumn} from 'typeorm';

import {type IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

@Entity('idempotency_keys')
export class IdempotencyKeyEntity implements IIdempotencyKey {
  @PrimaryColumn({type: 'varchar'})
  key!: IIdempotencyKey['key'];

  @Column({type: 'varchar'})
  operationId!: IIdempotencyKey['operationId'];

  @Column({name: 'request_hash_v2', type: 'varchar'})
  requestHash!: IIdempotencyKey['requestHash'];

  @Column({name: 'response_v2', type: 'jsonb'})
  response!: IIdempotencyKey['response'];

  @Column({type: 'integer'})
  responseVersion!: IIdempotencyKey['responseVersion'];

  @Index()
  @Column({type: 'timestamptz'})
  expiresAt!: IIdempotencyKey['expiresAt'];

  @Column({type: 'timestamptz'})
  createdAt!: IIdempotencyKey['createdAt'];
}
