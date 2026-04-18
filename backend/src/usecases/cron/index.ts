import {FetchDailyCurrencyRatesUseCase} from './fetch-daily-currency-rates.usecase';
import {CleanupIdempotencyKeysUseCase} from './cleanup-idempotency-keys.usecase';

export const allCronUseCases = [FetchDailyCurrencyRatesUseCase, CleanupIdempotencyKeysUseCase];
