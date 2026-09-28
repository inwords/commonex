import { MigrationInterface, QueryRunner } from "typeorm";

export class Init1780439953760 implements MigrationInterface {
    name = 'Init1780439953760'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "expense"
            ADD "reverts_expense_id" character varying
        `);
        await queryRunner.query(`
            ALTER TABLE "expense"
            ADD "replaces_expense_id" character varying
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "expense" DROP COLUMN "replaces_expense_id"
        `);
        await queryRunner.query(`
            ALTER TABLE "expense" DROP COLUMN "reverts_expense_id"
        `);
    }

}
