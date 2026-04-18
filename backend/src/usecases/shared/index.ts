import {FetchAndSaveCurrencyRateSharedUseCase} from './fetch-and-save-currency-rate.usecase';
import {IdempotencySharedUseCase} from './idempotency.usecase';

export const allSharedUseCases = [FetchAndSaveCurrencyRateSharedUseCase, IdempotencySharedUseCase];
