import {DataSource} from 'typeorm';

import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyCode, ICurrency} from '#domain/entities/currency.entity';
import {IEventShareToken} from '#domain/entities/event-share-token.entity';
import {IEvent} from '#domain/entities/event.entity';
import {IExpense} from '#domain/entities/expense.entity';
import {IIdempotencyKey} from '#domain/entities/idempotency-key.entity';
import {IUserInfo} from '#domain/entities/user-info.entity';

import {CurrencyRateRepository} from '../currency-rate.repository';
import {CurrencyRepository} from '../currency.repository';
import {EventShareTokenRepository} from '../event-share-token.repository';
import {EventRepository} from '../event.repository';
import {ExpenseRepository} from '../expense.repository';
import {IdempotencyKeyRepository} from '../idempotency-key.repository';
import {UserInfoRepository} from '../user-info.repository';

interface Repositories {
  currencyRate: CurrencyRateRepository;
  currency: CurrencyRepository;
  eventShareToken: EventShareTokenRepository;
  event: EventRepository;
  expense: ExpenseRepository;
  idempotencyKey: IdempotencyKeyRepository;
  userInfo: UserInfoRepository;
}

type RepositoryContractCase = [
  name: string,
  invoke: (repositories: Repositories, trx: ITransaction) => Promise<unknown>,
];

describe('PostgreSQL repository transaction context contract', () => {
  const cases: RepositoryContractCase[] = [
    ['CurrencyRate.findByDate', ({currencyRate}, trx): Promise<unknown> => currencyRate.findByDate('2026-01-01', trx)],
    [
      'CurrencyRate.findCurrenciesWithRatesVersionByDate',
      ({currencyRate}, trx): Promise<unknown> =>
        currencyRate.findCurrenciesWithRatesVersionByDate('2026-01-01', [CurrencyCode.USD], trx),
    ],
    ['CurrencyRate.findAll', ({currencyRate}, trx): Promise<unknown> => currencyRate.findAll({limit: 1}, trx)],
    ['CurrencyRate.insert', ({currencyRate}, trx): Promise<unknown> => currencyRate.insert({} as ICurrencyRate, trx)],
    ['Currency.findById', ({currency}, trx): Promise<unknown> => currency.findById('USD', trx)],
    ['Currency.findAll', ({currency}, trx): Promise<unknown> => currency.findAll({codes: [CurrencyCode.USD]}, trx)],
    ['Currency.insert', ({currency}, trx): Promise<unknown> => currency.insert({} as ICurrency, trx)],
    [
      'EventShareToken.findByToken',
      ({eventShareToken}, trx): Promise<unknown> => eventShareToken.findByToken('token', trx),
    ],
    [
      'EventShareToken.findByEventId',
      ({eventShareToken}, trx): Promise<unknown> => eventShareToken.findByEventId('event', trx),
    ],
    ['EventShareToken.findAll', ({eventShareToken}, trx): Promise<unknown> => eventShareToken.findAll({limit: 1}, trx)],
    [
      'EventShareToken.insert',
      ({eventShareToken}, trx): Promise<unknown> => eventShareToken.insert({} as IEventShareToken, trx),
    ],
    [
      'EventShareToken.deleteByToken',
      ({eventShareToken}, trx): Promise<unknown> => eventShareToken.deleteByToken('token', trx),
    ],
    [
      'EventShareToken.deleteExpiredBatch',
      ({eventShareToken}, trx): Promise<unknown> =>
        eventShareToken.deleteExpiredBatch({expiresBefore: new Date(), limit: 1}, trx),
    ],
    ['Event.findById', ({event}, trx): Promise<unknown> => event.findById('event', trx)],
    ['Event.findAll', ({event}, trx): Promise<unknown> => event.findAll({limit: 1}, trx)],
    ['Event.insert', ({event}, trx): Promise<unknown> => event.insert({} as IEvent, trx)],
    ['Event.update', ({event}, trx): Promise<unknown> => event.update('event', {}, trx)],
    ['Expense.findByEventId', ({expense}, trx): Promise<unknown> => expense.findByEventId('event', trx)],
    ['Expense.findAll', ({expense}, trx): Promise<unknown> => expense.findAll({limit: 1}, trx)],
    ['Expense.insert', ({expense}, trx): Promise<unknown> => expense.insert({} as IExpense, trx)],
    ['IdempotencyKey.findByKey', ({idempotencyKey}, trx): Promise<unknown> => idempotencyKey.findByKey('key', trx)],
    ['IdempotencyKey.findAll', ({idempotencyKey}, trx): Promise<unknown> => idempotencyKey.findAll({limit: 1}, trx)],
    [
      'IdempotencyKey.insert',
      ({idempotencyKey}, trx): Promise<unknown> => idempotencyKey.insert({} as IIdempotencyKey, trx),
    ],
    [
      'IdempotencyKey.tryAcquireLock',
      ({idempotencyKey}, trx): Promise<unknown> => idempotencyKey.tryAcquireLock('key', trx),
    ],
    ['IdempotencyKey.deleteByKey', ({idempotencyKey}, trx): Promise<unknown> => idempotencyKey.deleteByKey('key', trx)],
    [
      'IdempotencyKey.deleteExpiredBatch',
      ({idempotencyKey}, trx): Promise<unknown> =>
        idempotencyKey.deleteExpiredBatch({expiresBefore: new Date(), limit: 1}, trx),
    ],
    ['UserInfo.findByEventId', ({userInfo}, trx): Promise<unknown> => userInfo.findByEventId('event', trx)],
    ['UserInfo.findAll', ({userInfo}, trx): Promise<unknown> => userInfo.findAll({limit: 1}, trx)],
    ['UserInfo.insert', ({userInfo}, trx): Promise<unknown> => userInfo.insert({} as IUserInfo, trx)],
  ];

  it.each(cases)('%s rejects an invalid handle before selecting a repository', async (_name, invoke) => {
    const getRepository = jest.fn();
    const dataSource = {getRepository} as unknown as DataSource;
    const repositories = {
      currencyRate: new CurrencyRateRepository({dataSource, showQueryDetails: false}),
      currency: new CurrencyRepository({dataSource, showQueryDetails: false}),
      eventShareToken: new EventShareTokenRepository({dataSource, showQueryDetails: false}),
      event: new EventRepository({dataSource, showQueryDetails: false}),
      expense: new ExpenseRepository({dataSource, showQueryDetails: false}),
      idempotencyKey: new IdempotencyKeyRepository({dataSource, showQueryDetails: false}),
      userInfo: new UserInfoRepository({dataSource, showQueryDetails: false}),
    };
    const trx = {ctx: {}} as ITransaction;

    await expect(invoke(repositories, trx)).rejects.toBeInstanceOf(TypeError);
    expect(getRepository).not.toHaveBeenCalled();
  });
});
