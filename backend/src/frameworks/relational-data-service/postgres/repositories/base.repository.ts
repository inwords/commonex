import type {QueryBuilder} from 'typeorm';

import {IQueryDetails} from '#domain/abstracts/relational-data-service/types';

export class BaseRepository {
  private readonly showQueryDetails: boolean;

  constructor(showQueryDetails: boolean) {
    this.showQueryDetails = showQueryDetails;
  }

  // FIXME: возможно стоит сделать отдельной утилитой, чтобы не переусложнять код наследованием
  public getQueryDetails<T extends object>(queryBuilder: QueryBuilder<T>): IQueryDetails {
    if (!this.showQueryDetails) {
      return {queryString: undefined, queryParameters: undefined};
    }

    return {
      queryString: this.removeSchemaName(queryBuilder.getQuery()),
      queryParameters: queryBuilder.getParameters(),
    };
  }

  /** Удаляем префикс схемы из запроса, т.к. конкурентные тесты выполняются на случайном воркере и снэпшоты могут не совпадать */
  // FIXME: Детали тестирования просочились в production код. Есть возможность сделать как-то по-другому?
  private readonly removeSchemaName = (sql: string): string =>
    sql.replace(/"(test_schema_[0-9]+)"\."([\w_]+)"/, '"$2"');
}
