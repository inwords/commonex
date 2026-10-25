import { MigrationInterface, QueryRunner } from "typeorm";

export class Init1792936302199 implements MigrationInterface {
    name = 'Init1792936302199'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "event"
                RENAME COLUMN "protocol_version" TO "version"
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            ALTER TABLE "event"
                RENAME COLUMN "version" TO "protocol_version"
        `);
    }

}
