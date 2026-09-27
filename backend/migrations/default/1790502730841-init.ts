import { MigrationInterface, QueryRunner } from "typeorm";

export class Init1790502730841 implements MigrationInterface {
    name = 'Init1790502730841'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            DROP INDEX "idx__event_share_token__event_id"
        `);
        await queryRunner.query(`
            CREATE INDEX "idx__event_share_token__expires_at" ON "event_share_token" ("expires_at")
        `);
        await queryRunner.query(`
            CREATE INDEX "idx__event_share_token__event_id__expires_at" ON "event_share_token" ("event_id", "expires_at")
        `);
        await queryRunner.query(`
            CREATE INDEX "idx__expense__event_id" ON "expense" ("event_id")
        `);
        await queryRunner.query(`
            CREATE INDEX "idx__user_info__event_id" ON "user_info" ("event_id")
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            DROP INDEX "idx__user_info__event_id"
        `);
        await queryRunner.query(`
            DROP INDEX "idx__expense__event_id"
        `);
        await queryRunner.query(`
            DROP INDEX "idx__event_share_token__event_id__expires_at"
        `);
        await queryRunner.query(`
            DROP INDEX "idx__event_share_token__expires_at"
        `);
        await queryRunner.query(`
            CREATE INDEX "idx__event_share_token__event_id" ON "event_share_token" USING btree ("event_id")
        `);
    }

}
