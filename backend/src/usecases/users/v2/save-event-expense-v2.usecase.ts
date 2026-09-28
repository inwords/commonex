import {Injectable} from '@nestjs/common';

import {getCurrentDateWithoutTimeUTC, getDateWithoutTimeUTC} from '#packages/date-utils';
import {Result, error, isError, success} from '#packages/result';
import {UseCase} from '#packages/use-case';

import {EventServiceAbstract} from '#domain/abstracts/event-service/event-service';
import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {SupportedCurrencyServiceAbstract} from '#domain/abstracts/supported-currency-service/supported-currency-service';
import {IExpense, ISplitInfo} from '#domain/entities/expense.entity';
import {
  CurrencyNotFoundError,
  CurrencyRateNotFoundError,
  EventDeletedError,
  EventNotFoundError,
  ExpenseAlreadyRevertedError,
  ExpenseCorrectionConflictError,
  ExpenseReferenceNotFoundError,
  InconsistentExchangedAmountError,
  InvalidPinCodeError,
} from '#domain/errors/errors';
import {
  ExpenseCorrectionLinksInput,
  createExpenseReversal,
  validateExpenseCorrectionLinks as validateExpenseCorrectionLinksDomain,
} from '#domain/expense-correction/expense-correction';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';

import {IdempotencySharedUseCase, IdempotentInput} from '#usecases/shared/idempotency.usecase';

type SplitInfoInput = Omit<ISplitInfo, 'exchangedAmount'> & Partial<Pick<ISplitInfo, 'exchangedAmount'>>;

type InputCore = Omit<IExpense, 'createdAt' | 'id' | 'updatedAt' | 'isCustomRate' | 'splitInformation'> &
  Partial<Pick<IExpense, 'createdAt'>> & {
    splitInformation: SplitInfoInput[];
    pinCode: string;
    isCustomRate?: boolean;
  };

type Input = InputCore & IdempotentInput;
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

      const [event] = await this.rDataService.event.findById(input.eventId, {
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

      const referencedExpenseId = input.revertsExpenseId ?? input.replacesExpenseId;
      if (referencedExpenseId != null) {
        const correctionValidation = await this.validateExpenseCorrectionAndGetReference(
          input,
          referencedExpenseId,
          ctx,
        );

        if (isError(correctionValidation)) {
          return correctionValidation;
        }

        if (input.revertsExpenseId != null) {
          return this.saveExpense(createExpenseReversal(correctionValidation.value, input), ctx);
        }
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

        return this.saveExpense(expense, ctx);
      } else {
        const hasExchangedAmounts = input.splitInformation.some((s) => s.exchangedAmount !== undefined);

        if (hasExchangedAmounts) {
          if (requestedIsCustomRate === false && restInput.replacesExpenseId == null) {
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

          const expense = new ExpenseValueObject({
            ...restInput,
            splitInformation,
            isCustomRate: requestedIsCustomRate ?? true,
          }).value;

          return this.saveExpense(expense, ctx);
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
              exchangedAmount: Number((splitInfo.amount * exchangeRate).toFixed(2)),
            });
          }

          const expense = new ExpenseValueObject({...restInput, splitInformation, isCustomRate: false}).value;

          return this.saveExpense(expense, ctx);
        }
      }
    });
  }

  private async validateExpenseCorrectionAndGetReference(
    input: ExpenseCorrectionLinksInput,
    referencedExpenseId: IExpense['id'],
    ctx: unknown,
  ): Promise<
    Result<IExpense, ExpenseAlreadyRevertedError | ExpenseCorrectionConflictError | ExpenseReferenceNotFoundError>
  > {
    const [referencedExpense] = await this.rDataService.expense.findById(referencedExpenseId, {ctx});
    const [existingCorrection] = await this.rDataService.expense.findCorrectionForReferencedExpense(
      input.eventId,
      referencedExpenseId,
      {ctx},
    );

    return validateExpenseCorrectionLinksDomain(input, referencedExpense, existingCorrection);
  }

  private async saveExpense(expense: IExpense, ctx: unknown): Promise<Output> {
    await this.rDataService.expense.insert(expense, {ctx});

    return success(expense);
  }
}
