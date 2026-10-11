import { MigrationInterface, QueryRunner } from "typeorm";

export class Init1791726271060 implements MigrationInterface {
    name = 'Init1791726271060'

    public async up(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            LOCK TABLE "event_share_token" IN ACCESS EXCLUSIVE MODE
        `);
        await queryRunner.query(`
            DELETE FROM "event_share_token"
            WHERE "token" IN (
                SELECT "token" FROM (
                    SELECT "token", ROW_NUMBER() OVER (
                        PARTITION BY "event_id"
                        ORDER BY "expires_at" DESC, "created_at" DESC, "token" ASC
                    ) AS "rank"
                    FROM "event_share_token"
                ) AS "ranked"
                WHERE "rank" > 1
            )
        `);
        await queryRunner.query(`
            CREATE UNIQUE INDEX "idx__event_share_token__event_id" ON "event_share_token" ("event_id")
        `);
    }

    public async down(queryRunner: QueryRunner): Promise<void> {
        await queryRunner.query(`
            DROP INDEX "idx__event_share_token__event_id"
        `);
    }

}
