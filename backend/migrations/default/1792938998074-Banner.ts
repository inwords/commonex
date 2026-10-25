import {MigrationInterface, QueryRunner} from 'typeorm';

export class Banner1792938998074 implements MigrationInterface {
  name = 'Banner1792938998074';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`
      CREATE TABLE "banner" (
        "id" SERIAL NOT NULL,
        "type" character varying NOT NULL,
        "text" text NOT NULL,
        "platform" character varying NOT NULL,
        "min_version_code" integer NOT NULL DEFAULT 0,
        "max_version_code" integer,
        "enabled" boolean NOT NULL DEFAULT false,
        CONSTRAINT "pk__banner__id" PRIMARY KEY ("id"),
        CONSTRAINT "ck__banner__type" CHECK (type IN ('general_warning', 'update_required')),
        CONSTRAINT "ck__banner__platform" CHECK (platform IN ('Android', 'iOS')),
        CONSTRAINT "ck__banner__text" CHECK (text ~ '[^[:space:]]'),
        CONSTRAINT "ck__banner__version_range" CHECK (
          min_version_code >= 0 AND (max_version_code IS NULL OR max_version_code >= min_version_code)
        )
      )
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP TABLE "banner"');
  }
}
