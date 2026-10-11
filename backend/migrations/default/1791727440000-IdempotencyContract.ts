import {MigrationInterface, QueryRunner} from 'typeorm';

export class IdempotencyContract1791727440000 implements MigrationInterface {
  name = 'IdempotencyContract1791727440000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`LOCK TABLE "idempotency_keys" IN ACCESS EXCLUSIVE MODE`);
    await queryRunner.query(`
      DELETE FROM "idempotency_keys"
      WHERE "operation_id" IS NULL AND "request_hash_v2" IS NULL
        AND "response_v2" IS NULL AND "response_version" IS NULL
        AND LEAST("expires_at", "created_at" + INTERVAL '5 minutes') <= CURRENT_TIMESTAMP
    `);
    await queryRunner.query(`
      DO $$ BEGIN
        IF EXISTS (
          SELECT 1 FROM "idempotency_keys"
          WHERE "operation_id" IS NULL OR "request_hash_v2" IS NULL
            OR "response_v2" IS NULL OR "response_version" IS NULL
        ) THEN
          RAISE EXCEPTION 'Contract migration blocked: incomplete versioned idempotency records remain';
        END IF;
      END $$
    `);
    await queryRunner.query(`
      ALTER TABLE "idempotency_keys"
        DROP COLUMN "url", DROP COLUMN "request_hash", DROP COLUMN "response", DROP COLUMN "status_code",
        ALTER COLUMN "operation_id" SET NOT NULL,
        ALTER COLUMN "request_hash_v2" SET NOT NULL,
        ALTER COLUMN "response_v2" SET NOT NULL,
        ALTER COLUMN "response_version" SET NOT NULL
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    // Restores expand-phase schema only; removed legacy payloads and expired rows cannot be recovered.
    await queryRunner.query(`
      ALTER TABLE "idempotency_keys"
        ADD COLUMN "url" character varying,
        ADD COLUMN "request_hash" character varying,
        ADD COLUMN "response" jsonb,
        ADD COLUMN "status_code" integer,
        ALTER COLUMN "operation_id" DROP NOT NULL,
        ALTER COLUMN "request_hash_v2" DROP NOT NULL,
        ALTER COLUMN "response_v2" DROP NOT NULL,
        ALTER COLUMN "response_version" DROP NOT NULL
    `);
  }
}
