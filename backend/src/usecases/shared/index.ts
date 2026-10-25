import {CheckEventVersionSharedUseCase} from './check-event-version.usecase';
import {FetchAndSaveCurrencyRateSharedUseCase} from './fetch-and-save-currency-rate.usecase';
import {IdempotencySharedUseCase} from './idempotency.usecase';
import {SaveEventExpenseSharedUseCase} from './save-event-expense.usecase';

export const allSharedUseCases = [
  FetchAndSaveCurrencyRateSharedUseCase,
  CheckEventVersionSharedUseCase,
  IdempotencySharedUseCase,
  SaveEventExpenseSharedUseCase,
];
