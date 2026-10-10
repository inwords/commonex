import type {DataSource, EntityTarget, ObjectLiteral, QueryBuilder, Repository} from 'typeorm';

import {IQueryDetails, ITransaction} from '#domain/abstracts/relational-data-service/types';

import {resolveTransactionManager} from '../transaction-context';

export class BaseRepository {
  private readonly showQueryDetails: boolean;

  constructor(showQueryDetails: boolean) {
    this.showQueryDetails = showQueryDetails;
  }

  public getQueryDetails<T extends object>(queryBuilder: QueryBuilder<T>): IQueryDetails {
    if (!this.showQueryDetails) {
      return {queryString: undefined, queryParameters: undefined};
    }

    return {
      queryString: queryBuilder.getQuery(),
      queryParameters: queryBuilder.getParameters(),
    };
  }

  protected getRepository<TEntity extends ObjectLiteral>(
    dataSource: DataSource,
    entity: EntityTarget<TEntity>,
    trx?: ITransaction,
  ): Repository<TEntity> {
    const manager = resolveTransactionManager(dataSource, trx);
    return manager === undefined ? dataSource.getRepository(entity) : manager.getRepository(entity);
  }
}
