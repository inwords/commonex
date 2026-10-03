import { MigrationInterface, QueryRunner } from "typeorm";

export class Init1791040619671 implements MigrationInterface {
    name = 'Init1791040619671'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ADD "operation_id" character varying
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ADD "request_hash_v2" character varying
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ADD "response_v2" jsonb
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ADD "response_version" integer
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "url" DROP NOT NULL
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "request_hash" DROP NOT NULL
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "response" DROP NOT NULL
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "status_code" DROP NOT NULL
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "status_code"
            SET NOT NULL
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "response"
            SET NOT NULL
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "request_hash"
            SET NOT NULL
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys"
            ALTER COLUMN "url"
            SET NOT NULL
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys" DROP COLUMN "response_version"
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys" DROP COLUMN "response_v2"
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys" DROP COLUMN "request_hash_v2"
        `);
        await queryRunner.query(`
            ALTER TABLE "idempotency_keys" DROP COLUMN "operation_id"
        `);
    }

}
