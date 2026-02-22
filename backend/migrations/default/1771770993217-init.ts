import {MigrationInterface, QueryRunner} from 'typeorm';

export class AddIsCustomRateColumn1771770993217 implements MigrationInterface {
  name = 'AddIsCustomRateColumn1771770993217';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
            ALTER TABLE "expense"
            ADD "is_custom_rate" boolean NOT NULL DEFAULT false
        `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
            ALTER TABLE "expense" DROP COLUMN "is_custom_rate"
        `);
  }
}
