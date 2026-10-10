import {DataSource} from 'typeorm';

import {UserInfoRepositoryAbstract} from '#domain/abstracts/relational-data-service/repositories/user-info.repository';
import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';
import {IUserInfo} from '#domain/entities/user-info.entity';

import {UserInfoEntity} from '#frameworks/relational-data-service/postgres/entities/user-info.entity';
import {BaseRepository} from '#frameworks/relational-data-service/postgres/repositories/base.repository';

export class UserInfoRepository extends BaseRepository implements UserInfoRepositoryAbstract {
  readonly dataSource: DataSource;

  private readonly queryName = 'user_info';

  constructor({dataSource, showQueryDetails}: {dataSource: DataSource; showQueryDetails: boolean}) {
    super(showQueryDetails);
    this.dataSource = dataSource;
  }

  readonly findByEventId: UserInfoRepositoryAbstract['findByEventId'] = async (
    eventId: IUserInfo['eventId'],
    trx,
  ): Promise<[result: IUserInfo[], queryDetails: IQueryDetails]> => {
    let query = this.getRepository(this.dataSource, UserInfoEntity, trx).createQueryBuilder(this.queryName);

    query = query.where(`${this.queryName}.event_id = :eventId`, {
      eventId,
    });

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  public findAll: UserInfoRepositoryAbstract['findAll'] = async (input, trx) => {
    const {limit} = input;
    let query = this.getRepository(this.dataSource, UserInfoEntity, trx).createQueryBuilder(this.queryName);

    query = query.limit(limit);

    const queryDetails = this.getQueryDetails(query);
    const result = await query.getMany();

    return [result, queryDetails];
  };

  readonly insert: UserInfoRepositoryAbstract['insert'] = async (
    input: IUserInfo | IUserInfo[],
    trx,
  ): Promise<[result: undefined, queryDetails: IQueryDetails]> => {
    const query = this.getRepository(this.dataSource, UserInfoEntity, trx).createQueryBuilder().insert().values(input);
    const queryDetails = this.getQueryDetails(query);

    await query.execute();

    return [undefined, queryDetails];
  };
}
