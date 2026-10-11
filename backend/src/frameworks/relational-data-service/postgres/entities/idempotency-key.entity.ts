import {Column, Entity, Index, PrimaryColumn} from 'typeorm';

import {type IIdempotencyKey} from '#domain/entities/idempotency-key.entity';

@Entity('idempotency_keys')
export class IdempotencyKeyEntity implements IIdempotencyKey {
  @PrimaryColumn({type: 'varchar'})
  key!: IIdempotencyKey['key'];

  // Retained until the separate contract migration; excluded from application reads and writes.
  @Column({type: 'varchar', nullable: true, select: false, insert: false, update: false})
  url!: string | null;

  @Column({name: 'request_hash', type: 'varchar', nullable: true, select: false, insert: false, update: false})
  legacyRequestHash!: string | null;

  @Column({name: 'response', type: 'jsonb', nullable: true, select: false, insert: false, update: false})
  legacyResponse!: object | null;

  @Column({type: 'integer', nullable: true, select: false, insert: false, update: false})
  statusCode!: number | null;

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
