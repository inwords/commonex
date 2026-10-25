import {IQueryDetails, ITransaction} from '#domain/abstracts/relational-data-service/types';
import {IBanner} from '#domain/entities/banner.entity';
import {IClientVersion} from '#domain/entities/client-version';

export abstract class BannerRepositoryAbstract {
  /** Returns matching configuration in descending ID order. */
  abstract findAll: (
    input: {client?: IClientVersion; enabled?: boolean},
    trx?: ITransaction,
  ) => Promise<[result: IBanner[], queryDetails: IQueryDetails]>;

  abstract insert: (
    input: IBanner | IBanner[],
    trx?: ITransaction,
  ) => Promise<[result: undefined, queryDetails: IQueryDetails]>;
}
