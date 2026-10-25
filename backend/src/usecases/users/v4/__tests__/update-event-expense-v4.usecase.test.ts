import {isError} from '#packages/result';

import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {CurrencyCode, ICurrency} from '#domain/entities/currency.entity';
import {EventProtocolVersion} from '#domain/entities/event-protocol-version.enum';
import {IEvent} from '#domain/entities/event.entity';
import {ExpenseType, IExpense} from '#domain/entities/expense.entity';
import {CurrencyNotFoundError, ExpenseVersionConflictError, IdempotencyHashMismatchError} from '#domain/errors/errors';

import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';

import {EventService} from '#frameworks/event-service/event-service';
import {appDbConfig} from '#frameworks/relational-data-service/postgres/config';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';
import {SupportedCurrencyService} from '#frameworks/supported-currency-service/supported-currency-service';

import {truncateAllTables} from '#test-support/db';
import {prepareInitRelationalState} from '#test-support/relational-state';

import {UpdateEventExpenseV4UseCase} from '../update-event-expense-v4.usecase';

describe('UpdateEventExpenseV4UseCase', () => {
  let relationalDataService: RelationalDataService;
  let useCase: UpdateEventExpenseV4UseCase;
  const event: IEvent = {
    id: 'event-1',
    name: 'Dinner',
    currencyId: 'currency-2',
    pinCode: '1234',
    version: EventProtocolVersion.ExpenseEditing,
    createdAt: new Date('2026-10-01T00:00:00Z'),
    updatedAt: new Date('2026-10-01T00:00:00Z'),
    deletedAt: null,
  };
  const expense: IExpense = {
    id: 'expense-1',
    version: 1,
    eventId: event.id,
    currencyId: 'currency-1',
    description: 'Dinner',
    userWhoPaidId: 'user-1',
    expenseType: ExpenseType.Expense,
    splitInformation: [{userId: 'user-1', amount: 50, exchangedAmount: 75}],
    isCustomRate: true,
    createdAt: new Date('2026-10-01T12:00:00Z'),
    updatedAt: new Date('2026-10-01T12:00:00Z'),
  };
  const baseInput = {
    eventId: event.id,
    id: expense.id,
    expectedVersion: 1,
    pinCode: event.pinCode,
    description: 'Dinner with friends',
    userWhoPaidId: expense.userWhoPaidId,
    currencyId: expense.currencyId,
    splitInformation: [{userId: 'user-1', amount: 50, exchangedAmount: 75}],
    createdAt: expense.createdAt,
  };
  const currencies: ICurrency[] = [
    {
      id: 'currency-1',
      code: CurrencyCode.EUR,
      createdAt: event.createdAt,
      updatedAt: event.updatedAt,
    },
    {
      id: 'currency-2',
      code: CurrencyCode.USD,
      createdAt: event.createdAt,
      updatedAt: event.updatedAt,
    },
  ];
  const currencyRates: ICurrencyRate[] = [
    {
      date: '2026-10-01',
      rate: {USD: 1, EUR: 0.85},
      createdAt: event.createdAt,
      updatedAt: event.updatedAt,
    },
  ];

  beforeAll(async () => {
    relationalDataService = new RelationalDataService({dbConfig: appDbConfig, showQueryDetails: false});
    useCase = new UpdateEventExpenseV4UseCase(
      relationalDataService,
      new EventService(),
      new SupportedCurrencyService(),
      new IdempotencySharedUseCase(relationalDataService),
    );
    await relationalDataService.initialize();
  });

  afterAll(async () => {
    await relationalDataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(relationalDataService.dataSource);
    await prepareInitRelationalState({
      rDataService: relationalDataService,
      initState: {
        events: [event],
        currencies,
        currencyRates,
        userInfos: [
          {id: 'user-1', name: 'Alice', eventId: event.id, createdAt: event.createdAt, updatedAt: event.updatedAt},
        ],
        expenses: [expense],
      },
    });
  });

  it('preserves exchanged amounts when only the description changes', async () => {
    const result = await useCase.execute(baseInput);
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);

    expect(result).toMatchObject({
      result: 'success',
      value: {id: expense.id, version: 2, description: baseInput.description, isCustomRate: true},
    });
    expect(history).toHaveLength(2);
    expect(history[0]).toEqual(expense);
    expect(history[1]?.splitInformation).toEqual(expense.splitInformation);
  });

  it('rejects expense editing for legacy events before idempotency replay', async () => {
    await relationalDataService.event.update(event.id, {version: EventProtocolVersion.Legacy});

    const result = await useCase.execute({...baseInput, idempotencyKey: 'legacy-event-revision'});
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);

    expect(result).toMatchObject({result: 'error', error: {name: 'ExpenseEditingNotSupportedError'}});
    expect(history).toEqual([expense]);
  });

  it('preserves custom exchanged amounts when the unchanged split omits them', async () => {
    const result = await useCase.execute({...baseInput, splitInformation: [{userId: 'user-1', amount: 50}]});

    expect(result).toMatchObject({
      result: 'success',
      value: {version: 2, isCustomRate: true, splitInformation: [{userId: 'user-1', amount: 50, exchangedAmount: 75}]},
    });
  });

  it('stores a custom exchanged amount change for the same native amount', async () => {
    const result = await useCase.execute({
      ...baseInput,
      isCustomRate: true,
      splitInformation: [{userId: 'user-1', amount: 50, exchangedAmount: 85}],
    });

    expect(result).toMatchObject({
      result: 'success',
      value: {version: 2, isCustomRate: true, splitInformation: [{userId: 'user-1', amount: 50, exchangedAmount: 85}]},
    });
  });

  it('switches a custom rate to automatic conversion without changing native amounts', async () => {
    const result = await useCase.execute({...baseInput, isCustomRate: false});

    expect(result).toMatchObject({
      result: 'success',
      value: {
        version: 2,
        isCustomRate: false,
        splitInformation: [{userId: 'user-1', amount: 50, exchangedAmount: 58.82}],
      },
    });
  });

  it('rejects an unsupported currency even when a custom rate is supplied', async () => {
    const result = await useCase.execute({
      ...baseInput,
      currencyId: 'unsupported-currency',
      isCustomRate: true,
      splitInformation: [{userId: 'user-1', amount: 50, exchangedAmount: 75}],
    });

    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.error).toBeInstanceOf(CurrencyNotFoundError);
    }
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);
    expect(history).toHaveLength(1);
  });

  it('returns the current expense when the expected version is stale', async () => {
    await useCase.execute(baseInput);
    const result = await useCase.execute({...baseInput, description: 'Stale edit'});

    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.error).toBeInstanceOf(ExpenseVersionConflictError);
      expect((result.error as ExpenseVersionConflictError).currentExpense).toMatchObject({
        id: expense.id,
        version: 2,
        description: baseInput.description,
      });
    }
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);
    expect(history).toHaveLength(2);
  });

  it('allows only one concurrent revision for the same expected version', async () => {
    const results = await Promise.all([
      useCase.execute({...baseInput, description: 'Concurrent edit A'}),
      useCase.execute({...baseInput, description: 'Concurrent edit B'}),
    ]);

    expect(results.filter((result) => !isError(result))).toHaveLength(1);
    expect(results.filter((result) => isError(result))).toHaveLength(1);
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);
    expect(history).toHaveLength(2);
    expect(history[1]?.version).toBe(2);
  });

  it('replays the original revision snapshot for an idempotent retry', async () => {
    const input = {
      ...baseInput,
      idempotencyKey: 'revise-expense-replay',
    };

    const first = await useCase.execute(input);
    const retry = await useCase.execute(input);
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);

    expect(retry).toEqual(first);
    expect(history).toHaveLength(2);
    expect(history[1]).toEqual(expect.objectContaining({version: 2, description: baseInput.description}));
  });

  it('rejects an idempotent retry with a different revision payload', async () => {
    const input = {
      ...baseInput,
      idempotencyKey: 'revise-expense-mismatch',
    };
    await useCase.execute(input);

    const retry = await useCase.execute({...input, description: 'Different revision'});
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);

    expect(isError(retry)).toBe(true);
    if (isError(retry)) {
      expect(retry.error).toBeInstanceOf(IdempotencyHashMismatchError);
    }
    expect(history).toHaveLength(2);
  });

  it('rejects an expired idempotency retry with a stale expected version', async () => {
    await useCase.execute(baseInput);
    await prepareInitRelationalState({
      rDataService: relationalDataService,
      initState: {
        idempotencyKeys: [
          {
            key: 'revise-expense-expired',
            operationId: 'event.expense.revise.v4',
            requestHash: 'expired-hash',
            response: {},
            responseVersion: 1,
            createdAt: new Date('2026-09-30T00:00:00Z'),
            expiresAt: new Date('2026-09-30T00:05:00Z'),
          },
        ],
      },
    });

    const result = await useCase.execute({
      ...baseInput,
      idempotencyKey: 'revise-expense-expired',
    });
    const [history] = await relationalDataService.expense.findHistoryById(event.id, expense.id);

    expect(isError(result)).toBe(true);
    if (isError(result)) {
      expect(result.error).toBeInstanceOf(ExpenseVersionConflictError);
    }
    expect(history).toHaveLength(2);
  });
});
