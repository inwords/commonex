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
        CONSTRAINT "CHK_f35f6d341841d060f3bc5da794" CHECK (type IN ('general_warning', 'update_required')),
        CONSTRAINT "CHK_4c4e43032848a65a6824ea783f" CHECK (platform IN ('Android', 'iOS')),
        CONSTRAINT "CHK_6bf2ecef8526ca1febf48c9e0c" CHECK (text ~ '[^[:space:]]'),
        CONSTRAINT "CHK_b864d8406cd6681b09da8783e6" CHECK (
          min_version_code >= 0 AND (max_version_code IS NULL OR max_version_code >= min_version_code)
        )
      )
    `);
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP TABLE "banner"');
  }
}
