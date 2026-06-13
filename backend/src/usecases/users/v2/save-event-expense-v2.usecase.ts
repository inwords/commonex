import {Injectable} from '@nestjs/common';
import {UseCase} from '#packages/use-case';

import {getCurrentDateWithoutTimeUTC, getDateWithoutTimeUTC} from '#packages/date-utils';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {IdempotencySharedUseCase} from '#usecases/shared/idempotency.usecase';
import {IExpense, ISplitInfo} from '#domain/entities/expense.entity';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';
import {Result, success, error, isError} from '#packages/result';
import {
  EventNotFoundError,
  EventDeletedError,
  InvalidPinCodeError,
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  InconsistentExchangedAmountError,
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
  ExpenseReferenceNotFoundError,
} from '#domain/errors/errors';
import {validateExpenseCorrectionLinks as validateExpenseCorrectionLinksDomain} from '#domain/expense-correction/expense-correction';

type SplitInfoInput = Omit<ISplitInfo, 'exchangedAmount'> & Partial<Pick<ISplitInfo, 'exchangedAmount'>>;

type InputCore = Omit<IExpense, 'createdAt' | 'id' | 'updatedAt' | 'isCustomRate' | 'splitInformation'> &
  Partial<Pick<IExpense, 'createdAt'>> & {
    splitInformation: Array<SplitInfoInput>;
    pinCode: string;
    isCustomRate?: boolean;
  };

type Input = InputCore & {
  idempotencyKey?: string;
  url: string;
};
type Output = Result<
  IExpense,
  | EventNotFoundError
  | EventDeletedError
  | InvalidPinCodeError
  | CurrencyNotFoundError
  | CurrencyRateNotFoundError
  | InconsistentExchangedAmountError
  | ExpenseAlreadyRevertedError
  | ExpenseCorrectionConflictError
  | ExpenseReferenceNotFoundError
>;

@Injectable()
export class SaveEventExpenseV2UseCase implements UseCase<Input, Output> {
  constructor(
    private readonly rDataService: RelationalDataServiceAbstract,
    private readonly eventService: EventServiceAbstract,
    private readonly supportedCurrencyService: SupportedCurrencyServiceAbstract,
    private readonly idempotencyUseCase: IdempotencySharedUseCase,
  ) {}

  public async execute(input: Input): Promise<Output> {
    const {idempotencyKey, url, ...core} = input;
    return this.idempotencyUseCase.execute(idempotencyKey, url, core, () => this.executeCore(core));
  }

  private async executeCore(input: InputCore): Promise<Output> {
    return this.rDataService.transaction(async (ctx) => {
      const {pinCode, isCustomRate: requestedIsCustomRate, ...restInput} = input;

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

      const correctionValidation = await this.validateExpenseCorrectionLinks(restInput, ctx);

      if (isError(correctionValidation)) {
        return correctionValidation;
      }

      if (event.currencyId === input.currencyId) {
        const splitInformation: ISplitInfo[] = [];

        for (const splitInfo of input.splitInformation) {
          splitInformation.push({
            ...splitInfo,
            exchangedAmount: splitInfo.amount,
          });
        }

        const expense = new ExpenseValueObject({...restInput, splitInformation, isCustomRate: false}).value;

        await this.rDataService.expense.insert(expense, {ctx});

        return success(expense);
      } else {
        const hasCustomRate = input.splitInformation.some((s) => s.exchangedAmount !== undefined);

        if (hasCustomRate) {
          const isCorrection = restInput.revertsExpenseId != null || restInput.replacesExpenseId != null;
          if (requestedIsCustomRate === false && !isCorrection) {
            return error(new InconsistentExchangedAmountError());
          }

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

          const expense = new ExpenseValueObject({...restInput, splitInformation, isCustomRate: requestedIsCustomRate ?? true}).value;

          await this.rDataService.expense.insert(expense, {ctx});

          return success(expense);
        } else {
          const expenseCurrency = await this.supportedCurrencyService.findById(restInput.currencyId, {ctx});
          const eventCurrency = await this.supportedCurrencyService.findById(event.currencyId, {ctx});

          if (!eventCurrency || !expenseCurrency) {
            return error(new CurrencyNotFoundError());
          }

          const getDateForExchangeRate = restInput.createdAt
            ? getDateWithoutTimeUTC(new Date(restInput.createdAt))
            : getCurrentDateWithoutTimeUTC();

          const currencyRate = await this.supportedCurrencyService.findRateByDate(getDateForExchangeRate, {ctx});

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
              exchangedAmount: Number(Number(splitInfo.amount * exchangeRate).toFixed(2)),
            });
          }

          const expense = new ExpenseValueObject({...restInput, splitInformation, isCustomRate: false}).value;

          await this.rDataService.expense.insert(expense, {ctx});

          return success(expense);
        }
      }
    });
  }

  private async validateExpenseCorrectionLinks(
    input: Omit<InputCore, 'pinCode'>,
    ctx: unknown,
  ): Promise<Result<true, ExpenseAlreadyRevertedError | ExpenseCorrectionConflictError | ExpenseReferenceNotFoundError>> {
    const referencedExpenseId = input.revertsExpenseId ?? input.replacesExpenseId;

    if (referencedExpenseId == null) {
      return validateExpenseCorrectionLinksDomain(input, undefined, undefined);
    }

    const [referencedExpense] = await this.rDataService.expense.findById(referencedExpenseId, {ctx});
    const [existingCorrection] = await this.rDataService.expense.findCorrectionForReferencedExpense(
      input.eventId,
      referencedExpenseId,
      {ctx},
    );

    return validateExpenseCorrectionLinksDomain(input, referencedExpense, existingCorrection);
  }
}
