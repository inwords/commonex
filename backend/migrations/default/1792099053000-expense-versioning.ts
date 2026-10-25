import {MigrationInterface, QueryRunner} from 'typeorm';

export class ExpenseVersioning1792099053000 implements MigrationInterface {
  name = 'ExpenseVersioning1792099053000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`ALTER TABLE "expense" ADD "version" integer NOT NULL DEFAULT 1`);
    await queryRunner.query(`ALTER TABLE "expense" ALTER COLUMN "version" DROP DEFAULT`);
    await queryRunner.query(`ALTER TABLE "expense" DROP CONSTRAINT "pk__expense__id"`);
    await queryRunner.query(`ALTER TABLE "expense" ADD CONSTRAINT "pk__expense__id__version" PRIMARY KEY ("id", "version")`);
    await queryRunner.query(`DROP INDEX IF EXISTS "idx__expense__event_id"`);
    await queryRunner.query(`CREATE INDEX "idx__expense__event_id__id__version" ON "expense" ("event_id", "id", "version" DESC)`);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP INDEX "idx__expense__event_id__id__version"`);
    await queryRunner.query(`CREATE INDEX "idx__expense__event_id" ON "expense" ("event_id")`);
    await queryRunner.query(`ALTER TABLE "expense" DROP CONSTRAINT "pk__expense__id__version"`);
    await queryRunner.query(`ALTER TABLE "expense" ADD CONSTRAINT "pk__expense__id" PRIMARY KEY ("id")`);
    await queryRunner.query(`ALTER TABLE "expense" DROP COLUMN "version"`);
  }
}
