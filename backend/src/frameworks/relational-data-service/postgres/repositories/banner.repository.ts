import {DataSource} from 'typeorm';

import {BannerRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/banner.repository';

import {BannerEntity} from '#frameworks/relational-data-service/postgres/entities/banner.entity';
import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';

export class BannerRepository extends BaseRepository implements BannerRepositoryAbstract {
  constructor(
    private readonly dataSource: DataSource,
    showQueryDetails: boolean,
  ) {
    super(showQueryDetails);
  }

  readonly findAll: BannerRepositoryAbstract['findAll'] = async ({client, enabled}, trx) => {
    const query = this.getRepository(this.dataSource, BannerEntity, trx).createQueryBuilder('banner');

    if (enabled != null) {
      query.andWhere('banner.enabled = :enabled', {enabled});
    }
    if (client != null) {
      query.andWhere('banner.platform = :platform', {platform: client.platform});
      query.andWhere('banner.minVersionCode <= :versionCode', {versionCode: client.versionCode});
      query.andWhere('(banner.maxVersionCode IS NULL OR banner.maxVersionCode >= :versionCode)');
    }
    query.orderBy('banner.id', 'DESC');

    const details = this.getQueryDetails(query);
    return [await query.getMany(), details];
  };

  readonly insert: BannerRepositoryAbstract['insert'] = async (input, trx) => {
    const query = this.getRepository(this.dataSource, BannerEntity, trx)
      .createQueryBuilder()
      .insert()
      .into(BannerEntity, ['id', 'type', 'text', 'platform', 'minVersionCode', 'maxVersionCode', 'enabled'])
      .values(input);
    const details = this.getQueryDetails(query);
    await query.execute();
    return [undefined, details];
  };
}
