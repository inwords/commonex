import {MigrationInterface, QueryRunner} from 'typeorm';

export class Init1776518011496 implements MigrationInterface {
  name = 'Init1776518011496';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
            CREATE TABLE "idempotency_keys" (
                "key" character varying NOT NULL,
                "url" character varying NOT NULL,
                "request_hash" character varying NOT NULL,
                "response" jsonb NOT NULL,
                "status_code" integer NOT NULL,
                "expires_at" TIMESTAMP WITH TIME ZONE NOT NULL,
                "created_at" TIMESTAMP WITH TIME ZONE NOT NULL,
                CONSTRAINT "pk__idempotency_keys__key" PRIMARY KEY ("key")
            )
        `);
    await queryRunner.query(`
            CREATE INDEX "idx__idempotency_keys__expires_at" ON "idempotency_keys" ("expires_at")
        `);
    await queryRunner.query(`
            ALTER TABLE "expense"
            ALTER COLUMN "is_custom_rate" DROP DEFAULT
        `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
            ALTER TABLE "expense"
            ALTER COLUMN "is_custom_rate"
            SET DEFAULT false
        `);
    await queryRunner.query(`
            DROP INDEX "public"."idx__idempotency_keys__expires_at"
        `);
    await queryRunner.query(`
            DROP TABLE "idempotency_keys"
        `);
  }
}
