import type {QueryBuilder} from 'typeorm';

import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';

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
}
