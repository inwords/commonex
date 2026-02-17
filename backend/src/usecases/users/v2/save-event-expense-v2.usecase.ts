import {Injectable} from '@nestjs/common';
import {UseCase} from '#packages/use-case';

import {getCurrentDateWithoutTimeUTC, getDateWithoutTimeUTC} from '#packages/date-utils';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {IExpense, ISplitInfo} from '#domain/entities/expense.entity';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';
import {Result, success, error, isError} from '#packages/result';
import {
  EventNotFoundError,
  EventDeletedError,
  InvalidPinCodeError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
} from '#domain/errors/errors';

type Input = Omit<IExpense, 'createdAt' | 'id' | 'updatedAt'> &
  Partial<Pick<IExpense, 'createdAt'>> & {pinCode: string};
type Output = Result<
  IExpense,
  EventNotFoundError | EventDeletedError | InvalidPinCodeError | CurrencyNotFoundError | CurrencyRateNotFoundError
>;

@Injectable()
export class SaveEventExpenseV2UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
  ) {}

  public async execute(input: Input): Promise<Output> {
    return this.rDataService.transaction(async (ctx) => {
      const {pinCode, ...restInput} = input;

      const [event] = await this.rDataService.event.findById(restInput.eventId, {
        ctx,
        lock: 'pessimistic_write',
        onLocked: 'nowait',
      });

      if (!this.eventService.isEventExists(event)) {
        return error(new EventNotFoundError());
      }

      const notDeletedResult = this.eventService.isEventNotDeleted(event);

      if (isError(notDeletedResult)) {
        return notDeletedResult;
      }

      const pinCodeResult = this.eventService.isValidPinCode(event, pinCode);

      if (isError(pinCodeResult)) {
        return pinCodeResult;
      }

      if (event.currencyId === input.currencyId) {
        const splitInformation: ISplitInfo[] = [];

        for (const splitInfo of input.splitInformation) {
          splitInformation.push({
            ...splitInfo,
            exchangedAmount: splitInfo.amount,
          });
        }

        const expense = new ExpenseValueObject({...restInput, splitInformation}).value;

        await this.rDataService.expense.insert(expense, {ctx});

        return success(expense);
      } else {
        const [expenseCurrencyCode] = await this.rDataService.currency.findById(restInput.currencyId, {ctx});
        const [eventCurrencyCode] = await this.rDataService.currency.findById(event.currencyId, {ctx});

        if (!eventCurrencyCode || !expenseCurrencyCode) {
          return error(new CurrencyNotFoundError());
        }

        const getDateForExchangeRate = restInput.createdAt
          ? getDateWithoutTimeUTC(new Date(restInput.createdAt))
          : getCurrentDateWithoutTimeUTC();

        const [currencyRate] = await this.rDataService.currencyRate.findByDate(getDateForExchangeRate, {ctx});

        if (!currencyRate) {
          return error(new CurrencyRateNotFoundError());
        }

        const expenseCurrencyRate = currencyRate.rate[expenseCurrencyCode.code];
        const eventCurrencyRate = currencyRate.rate[eventCurrencyCode.code];

        if (expenseCurrencyRate === undefined || eventCurrencyRate === undefined) {
          return error(new CurrencyRateNotFoundError());
        }

        const exchangeRate = eventCurrencyRate / expenseCurrencyRate;

        const splitInformation: ISplitInfo[] = [];

        for (const splitInfo of input.splitInformation) {
          splitInformation.push({
            ...splitInfo,
            exchangedAmount: Number(Number(splitInfo.amount * exchangeRate).toFixed(2)),
          });
        }

        const expense = new ExpenseValueObject({...restInput, splitInformation}).value;

        await this.rDataService.expense.insert(expense, {ctx});

        return success(expense);
      }
    });
  }
}
