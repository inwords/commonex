import {Column, Entity, Index, PrimaryColumn} from 'typeorm';

import {type IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

@Entity('idempotency_keys')
export class IdempotencyKeyEntity implements IIdempotencyKey {
  @PrimaryColumn({type: 'varchar'})
  key!: IIdempotencyKey['key'];

  @Column({type: 'varchar', nullable: true})
  url!: IIdempotencyKey['url'];

  @Column({name: 'request_hash', type: 'varchar', nullable: true})
  legacyRequestHash!: IIdempotencyKey['legacyRequestHash'];

  @Column({name: 'response', type: 'jsonb', nullable: true})
  legacyResponse!: IIdempotencyKey['legacyResponse'];

  @Column({type: 'integer', nullable: true})
  statusCode!: IIdempotencyKey['statusCode'];

  @Column({type: 'varchar', nullable: true})
  operationId!: IIdempotencyKey['operationId'];

  @Column({name: 'request_hash_v2', type: 'varchar', nullable: true})
  requestHash!: IIdempotencyKey['requestHash'];

  @Column({name: 'response_v2', type: 'jsonb', nullable: true})
  response!: IIdempotencyKey['response'];

  @Column({type: 'integer', nullable: true})
  responseVersion!: IIdempotencyKey['responseVersion'];

  @Index()
  @Column({type: 'timestamptz'})
  expiresAt!: IIdempotencyKey['expiresAt'];

  @Column({type: 'timestamptz'})
  createdAt!: IIdempotencyKey['createdAt'];
}
