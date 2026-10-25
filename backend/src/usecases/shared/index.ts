import {CheckEventProtocolUseCase} from './check-event-protocol.usecase';
import {FetchAndSaveCurrencyRateSharedUseCase} from './fetch-and-save-currency-rate.usecase';
import {IdempotencySharedUseCase} from './idempotency.usecase';
import {SaveEventExpenseSharedUseCase} from './save-event-expense.usecase';

export const allSharedUseCases = [
  FetchAndSaveCurrencyRateSharedUseCase,
  CheckEventProtocolUseCase,
  IdempotencySharedUseCase,
  SaveEventExpenseSharedUseCase,
];
