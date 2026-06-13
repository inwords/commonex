import { MigrationInterface, QueryRunner } from "typeorm";

export class Init1780449953760 implements MigrationInterface {
    name = 'Init1780449953760'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            CREATE INDEX "IDX_expense_reverts_expense_id"
            ON "expense" ("reverts_expense_id")
        `);
        await queryRunner.query(`
            CREATE INDEX "IDX_expense_replaces_expense_id"
            ON "expense" ("replaces_expense_id")
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            DROP INDEX "IDX_expense_replaces_expense_id"
        `);
        await queryRunner.query(`
            DROP INDEX "IDX_expense_reverts_expense_id"
        `);
    }

}
