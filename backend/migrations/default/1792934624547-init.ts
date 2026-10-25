import { MigrationInterface, QueryRunner } from "typeorm";

export class Init1792934624547 implements MigrationInterface {
    name = 'Init1792934624547'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "event"
            ADD "protocol_version" integer NOT NULL DEFAULT '1'
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "event" DROP COLUMN "protocol_version"
        `);
    }

}
