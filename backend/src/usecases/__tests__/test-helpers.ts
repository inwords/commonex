import {IEvent} from '#domain/entities/event.entity';
import {IUserInfo} from '#domain/entities/user-info.entity';
import {IEventShareToken} from '#domain/entities/event-share-token.entity';
import {ICurrency} from '#domain/entities/currency.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';
import {IExpense} from '#domain/entities/expense.entity';
import {IIdempotencyKey} from '#domain/entities/idempotency-key.entity';
import {applyChanges, type StateChanges} from './utils-apply-changes-to-state';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

interface RelationalEntities {
  events: IEvent;
  userInfos: IUserInfo;
  eventShareTokens: IEventShareToken;
  currencies: ICurrency;
  currencyRates: ICurrencyRate;
  expenses: IExpense;
  idempotencyKeys: IIdempotencyKey;
}

type FindAllFn<T> = (rDataService: RelationalDataServiceAbstract) => Promise<T[]>;
type InsertFn<T> = (rDataService: RelationalDataServiceAbstract, values: T[]) => Promise<void>;

const entityOperations: {
  [K in keyof RelationalEntities]: {
    findAll: FindAllFn<RelationalEntities[K]>;
    insert: InsertFn<RelationalEntities[K]>;
  };
} = {
  events: {
    findAll: async (rDataService) => {
      const [result] = await rDataService.event.findAll({limit: 10000});
      return result;
    },
    insert: async (rDataService, values) => {
      for (const item of values) {
        await rDataService.event.insert(item);
      }
    },
  },
  userInfos: {
    findAll: async (rDataService) => {
      const [result] = await rDataService.userInfo.findAll({limit: 10000});
      return result;
    },
    insert: async (rDataService, values) => {
      await rDataService.userInfo.insert(values);
    },
  },
  eventShareTokens: {
    findAll: async (rDataService) => {
      const [result] = await rDataService.eventShareToken.findAll({limit: 10000});
      return result;
    },
    insert: async (rDataService, values) => {
      for (const item of values) {
        await rDataService.eventShareToken.insert(item);
      }
    },
  },
  currencies: {
    findAll: async (rDataService) => {
      const [result] = await rDataService.currency.findAll({limit: 10000});
      return result;
    },
    insert: async (rDataService, values) => {
      await rDataService.currency.insert(values);
    },
  },
  currencyRates: {
    findAll: async (rDataService) => {
      const [result] = await rDataService.currencyRate.findAll({limit: 10000});
      return result;
    },
    insert: async (rDataService, values) => {
      for (const item of values) {
        await rDataService.currencyRate.insert(item);
      }
    },
  },
  expenses: {
    findAll: async (rDataService) => {
      const [result] = await rDataService.expense.findAll({limit: 10000});
      return result;
    },
    insert: async (rDataService, values) => {
      for (const item of values) {
        await rDataService.expense.insert(item);
      }
    },
  },
  idempotencyKeys: {
    findAll: async (rDataService) => {
      const [result] = await rDataService.idempotencyKey.findAll({limit: 10000});
      return result;
    },
    insert: async (rDataService, values) => {
      for (const item of values) {
        await rDataService.idempotencyKey.insert(item);
      }
    },
  },
};

export type RelationalState = {
  [K in keyof RelationalEntities]?: RelationalEntities[K][];
};

export type RelationalStateChanges = {
  [K in keyof RelationalEntities]?: StateChanges<RelationalEntities[K]>;
};

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type TestCase<UseCase extends {execute: (...args: any[]) => any}> = {
  name: string;
  initRelationalState: RelationalState;
  input: Parameters<UseCase['execute']>[0];
  output: Awaited<ReturnType<UseCase['execute']>>;
  relationalStateChanges?: RelationalStateChanges;
};

export const prepareInitRelationalState = async ({
  rDataService,
  initState,
}: {
  rDataService: RelationalDataServiceAbstract;
  initState: RelationalState;
}): Promise<void> => {
  await Promise.all(
    Object.entries(initState).map(
      async <T extends keyof RelationalEntities>([_key, value]: [string, RelationalEntities[T][]]) => {
        const key = _key as T;
        await entityOperations[key].insert(rDataService, value);
      },
    ),
  );
};

export const validateFinalRelationalState = async ({
  rDataService,
  expectedState,
}: {
  rDataService: RelationalDataServiceAbstract;
  expectedState: RelationalState;
}): Promise<void> => {
  await Promise.all(
    Object.entries(entityOperations).map(async ([_key, value]) => {
      const key = _key as keyof RelationalEntities;
      try {
        const result = await value.findAll(rDataService);
        const expected = expectedState[key] ?? [];
        expect(result).toHaveLength(expected.length);
        expect(result).toEqual(expect.arrayContaining(expected.map((x) => expect.objectContaining(x))));
      } catch (error) {
        throw new Error(`Ошибка при проверке finalRelationalState: ${key}\n\n${(error as Error).message}`, {
          cause: error,
        });
      }
    }),
  );
};

export const validateRelationalStateChanges = async ({
  rDataService,
  initState,
  stateChanges,
}: {
  rDataService: RelationalDataServiceAbstract;
  initState: RelationalState;
  stateChanges: RelationalStateChanges;
}): Promise<void> => {
  const expectedState = {...initState};
  for (const [_key, value] of Object.entries(stateChanges)) {
    const key = _key as keyof RelationalEntities;
    // @ts-expect-error Type mismatch between generic RelationalEntities[K] and specific entity arrays
    expectedState[key] = applyChanges(expectedState[key] ?? [], value);
  }

  await validateFinalRelationalState({rDataService, expectedState});
};

export function useFakeTimers(time?: number): void {
  jest.useFakeTimers({
    doNotFake: [
      'hrtime',
      'nextTick',
      'performance',
      'queueMicrotask',
      'requestAnimationFrame',
      'cancelAnimationFrame',
      'requestIdleCallback',
      'cancelIdleCallback',
      'setImmediate',
      'clearImmediate',
      'setInterval',
      'clearInterval',
      'setTimeout',
      'clearTimeout',
    ],
    now: time ?? 1706745600000,
  });
}
