import {Injectable} from '@nestjs/common';

import {getCurrentDateWithoutTimeUTC, getDateWithoutTimeUTC} from '#packages/date-utils';
import {Result, error, success} from '#packages/result';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {IEvent} from '#domain/entities/event.entity';
import {IExpense, ISplitInfo} from '#domain/entities/expense.entity';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  InconsistentExchangedAmountError,
} from '#domain/errors/errors';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';

export type SaveEventExpenseInput = Omit<
  IExpense,
  'createdAt' | 'id' | 'version' | 'updatedAt' | 'isCustomRate' | 'splitInformation'
> &
  Partial<Pick<IExpense, 'createdAt'>> & {
    splitInformation: (Omit<ISplitInfo, 'exchangedAmount'> & Partial<Pick<ISplitInfo, 'exchangedAmount'>>)[];
  };
type Output = Result<IExpense, CurrencyNotFoundError | CurrencyRateNotFoundError | InconsistentExchangedAmountError>;

@Injectable()
export class SaveEventExpenseSharedUseCase {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
  ) {}

  public async execute(input: SaveEventExpenseInput, event: IEvent, trx: ITransaction): Promise<Output> {
    if (event.currencyId === input.currencyId) {
      const splitInformation: ISplitInfo[] = [];

      for (const splitInfo of input.splitInformation) {
        splitInformation.push({
          ...splitInfo,
          exchangedAmount: splitInfo.amount,
        });
      }

      const expense = new ExpenseValueObject({...input, splitInformation, isCustomRate: false}).value;

      await this.rDataService.expense.insert(expense, trx);

      return success(expense);
    } else {
      // Проверяем, передан ли exchangedAmount хотя бы в одном элементе
      const hasCustomRate = input.splitInformation.some((s) => s.exchangedAmount !== undefined);

      if (hasCustomRate) {
        // Кастомный курс - используем переданные exchangedAmount
        const splitInformation: ISplitInfo[] = [];
        for (const splitInfo of input.splitInformation) {
          if (splitInfo.exchangedAmount === undefined) {
            return error(new InconsistentExchangedAmountError());
          }
          splitInformation.push({
            userId: splitInfo.userId,
            amount: splitInfo.amount,
            exchangedAmount: splitInfo.exchangedAmount,
          });
        }

        const expense = new ExpenseValueObject({...input, splitInformation, isCustomRate: true}).value;

        await this.rDataService.expense.insert(expense, trx);

        return success(expense);
      } else {
        // Автоматический курс (существующая логика)
        const [expenseCurrency] = await this.rDataService.currency.findById(input.currencyId, trx);
        const [eventCurrency] = await this.rDataService.currency.findById(event.currencyId, trx);

        if (
          !this.supportedCurrencyService.isSupported(eventCurrency) ||
          !this.supportedCurrencyService.isSupported(expenseCurrency)
        ) {
          return error(new CurrencyNotFoundError());
        }

        const getDateForExchangeRate = input.createdAt
          ? getDateWithoutTimeUTC(new Date(input.createdAt))
          : getCurrentDateWithoutTimeUTC();

        const [currencyRate] = await this.rDataService.currencyRate.findByDate(getDateForExchangeRate, trx);

        if (!currencyRate) {
          return error(new CurrencyRateNotFoundError());
        }

        const expenseCurrencyRate = currencyRate.rate[expenseCurrency.code];
        const eventCurrencyRate = currencyRate.rate[eventCurrency.code];

        if (expenseCurrencyRate === undefined || eventCurrencyRate === undefined) {
          return error(new CurrencyRateNotFoundError());
        }

        const exchangeRate = eventCurrencyRate / expenseCurrencyRate;

        const splitInformation: ISplitInfo[] = [];

        for (const splitInfo of input.splitInformation) {
          splitInformation.push({
            ...splitInfo,
            exchangedAmount: Number((splitInfo.amount * exchangeRate).toFixed(2)),
          });
        }

        const expense = new ExpenseValueObject({...input, splitInformation, isCustomRate: false}).value;

        await this.rDataService.expense.insert(expense, trx);

        return success(expense);
      }
    }
  }
}
