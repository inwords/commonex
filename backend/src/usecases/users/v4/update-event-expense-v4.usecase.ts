import {Injectable} from '@nestjs/common';

import {getDateWithoutTimeUTC} from '#packages/date-utils';
import {Result, error, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {ITransaction} from '#domain/abstracts/relational-data-service/types';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {IEvent} from '#domain/entities/event.entity';
import {IExpense, ISplitInfo} from '#domain/entities/expense.entity';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  ExpenseNotFoundError,
  ExpenseParticipantsInvalidError,
  ExpenseVersionConflictError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
} from '#domain/errors/errors';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';

import {
  IdempotencyError,
  IdempotencyOperation,
  IdempotencySharedUseCase,
  IdempotentInput,
} from '#usecases/shared/idempotency.usecase';

type SplitInput = Omit<ISplitInfo, 'exchangedAmount'> & Partial<Pick<ISplitInfo, 'exchangedAmount'>>;
interface InputCore {
  eventId: IEvent['id'];
  id: IExpense['id'];
  expectedVersion: number;
  pinCode: IEvent['pinCode'];
  description: IExpense['description'];
  userWhoPaidId: IExpense['userWhoPaidId'];
  currencyId: IExpense['currencyId'];
  splitInformation: SplitInput[];
  createdAt: IExpense['createdAt'];
  isCustomRate?: boolean;
}
type Input = InputCore & IdempotentInput;
type Output = Result<
  IExpense,
  | EventNotFoundError
  | EventDeletedError
  | InvalidPinCodeError
  | ExpenseNotFoundError
  | ExpenseParticipantsInvalidError
  | ExpenseVersionConflictError
  | CurrencyNotFoundError
  | CurrencyRateNotFoundError
  | InconsistentExchangedAmountError
  | IdempotencyError
>;

@Injectable()
export class UpdateEventExpenseV4UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const {idempotencyKey, ...core} = input;
    const [event] = await this.rDataService.event.findById(core.eventId);
    const validation = this.eventService.isValidEvent(event, core.pinCode);
    if (isError(validation)) {
      return validation;
    }
    return this.idempotencyUseCase.execute(idempotencyKey, IdempotencyOperation.REVISE_EVENT_EXPENSE_V4, core, (trx) =>
      this.executeCore(core, trx),
    );
  }

  private async executeCore(input: InputCore, trx: ITransaction): Promise<Output> {
    const [event] = await this.rDataService.event.findById(input.eventId, {...trx, lock: 'pessimistic_write'});
    if (!event) {
      return error(new EventNotFoundError());
    }
    const validation = this.eventService.isValidEvent(event, input.pinCode);
    if (isError(validation)) {
      return validation;
    }

    const [current] = await this.rDataService.expense.findLatestById(input.eventId, input.id, trx);
    if (!current) {
      return error(new ExpenseNotFoundError());
    }
    if (current.version !== input.expectedVersion) {
      return error(new ExpenseVersionConflictError(current));
    }

    const [users] = await this.rDataService.userInfo.findByEventId(input.eventId, trx);
    const userIds = new Set(users.map(({id}) => id));
    if (!userIds.has(input.userWhoPaidId) || input.splitInformation.some(({userId}) => !userIds.has(userId))) {
      return error(new ExpenseParticipantsInvalidError());
    }

    const [[expenseCurrency], [eventCurrency]] = await Promise.all([
      this.rDataService.currency.findById(input.currencyId, trx),
      this.rDataService.currency.findById(event.currencyId, trx),
    ]);
    if (
      !this.supportedCurrencyService.isSupported(expenseCurrency) ||
      !this.supportedCurrencyService.isSupported(eventCurrency)
    ) {
      return error(new CurrencyNotFoundError());
    }

    const unchangedFinancialInputs =
      current.currencyId === input.currencyId &&
      current.createdAt.getTime() === input.createdAt.getTime() &&
      sameSplits(current.splitInformation, input.splitInformation);
    const requestedCustomRate = input.isCustomRate ?? (unchangedFinancialInputs && current.isCustomRate);
    const unchangedFinancialFields =
      unchangedFinancialInputs &&
      requestedCustomRate === current.isCustomRate &&
      (!requestedCustomRate ||
        input.splitInformation.every(({exchangedAmount}) => exchangedAmount === undefined) ||
        sameExchangedAmounts(current.splitInformation, input.splitInformation));
    let splitInformation: ISplitInfo[];
    let isCustomRate: boolean;
    if (unchangedFinancialFields) {
      splitInformation = current.splitInformation;
      isCustomRate = current.isCustomRate;
    } else {
      const calculated = await this.calculateSplits(event, input, requestedCustomRate, trx);
      if (isError(calculated)) {
        return calculated;
      }
      splitInformation = calculated.value.splitInformation;
      isCustomRate = calculated.value.isCustomRate;
    }

    const revision = new ExpenseValueObject({
      id: current.id,
      version: current.version + 1,
      eventId: current.eventId,
      expenseType: current.expenseType,
      description: input.description,
      userWhoPaidId: input.userWhoPaidId,
      currencyId: input.currencyId,
      splitInformation,
      isCustomRate,
      createdAt: input.createdAt,
    }).value;
    await this.rDataService.expense.insert(revision, trx);
    return success(revision);
  }

  private async calculateSplits(
    event: IEvent,
    input: InputCore,
    useCustomRate: boolean,
    trx: ITransaction,
  ): Promise<
    Result<
      {splitInformation: ISplitInfo[]; isCustomRate: boolean},
      CurrencyNotFoundError | CurrencyRateNotFoundError | InconsistentExchangedAmountError
    >
  > {
    if (event.currencyId === input.currencyId) {
      return success({
        splitInformation: input.splitInformation.map(({userId, amount}) => ({userId, amount, exchangedAmount: amount})),
        isCustomRate: false,
      });
    }
    if (useCustomRate) {
      const customSplits = input.splitInformation.filter(
        (split): split is typeof split & {exchangedAmount: number} => split.exchangedAmount !== undefined,
      );
      if (customSplits.length !== input.splitInformation.length) {
        return error(new InconsistentExchangedAmountError());
      }
      return success({
        splitInformation: customSplits.map(({userId, amount, exchangedAmount}) => ({
          userId,
          amount,
          exchangedAmount,
        })),
        isCustomRate: true,
      });
    }

    const [expenseCurrency] = await this.rDataService.currency.findById(input.currencyId, trx);
    const [eventCurrency] = await this.rDataService.currency.findById(event.currencyId, trx);
    if (
      !this.supportedCurrencyService.isSupported(expenseCurrency) ||
      !this.supportedCurrencyService.isSupported(eventCurrency)
    ) {
      return error(new CurrencyNotFoundError());
    }
    const rateDate = getDateWithoutTimeUTC(input.createdAt);
    const [rate] = await this.rDataService.currencyRate.findByDate(rateDate, trx);
    const expenseCurrencyRate = rate?.rate[expenseCurrency.code];
    const eventCurrencyRate = rate?.rate[eventCurrency.code];
    if (expenseCurrencyRate === undefined || eventCurrencyRate === undefined) {
      return error(new CurrencyRateNotFoundError());
    }
    const exchangeRate = eventCurrencyRate / expenseCurrencyRate;
    return success({
      splitInformation: input.splitInformation.map(({userId, amount}) => ({
        userId,
        amount,
        exchangedAmount: Number((amount * exchangeRate).toFixed(2)),
      })),
      isCustomRate: false,
    });
  }
}

const sameSplits = (current: ISplitInfo[], input: SplitInput[]): boolean => {
  const left = current.map(({userId, amount}) => [userId, amount] as const).sort(([a], [b]) => a.localeCompare(b));
  const right = input.map(({userId, amount}) => [userId, amount] as const).sort(([a], [b]) => a.localeCompare(b));
  return JSON.stringify(left) === JSON.stringify(right);
};

const sameExchangedAmounts = (current: ISplitInfo[], input: SplitInput[]): boolean => {
  const currentAmounts = new Map(current.map(({userId, exchangedAmount}) => [userId, exchangedAmount]));
  return input.every(({userId, exchangedAmount}) => currentAmounts.get(userId) === exchangedAmount);
};
