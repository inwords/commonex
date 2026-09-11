import {CleanupIdempotencyKeysUseCase} from './cleanup-idempotency-keys.usecase';
import {FetchDailyCurrencyRatesUseCase} from './fetch-daily-currency-rates.usecase';

export const allCronUseCases = [FetchDailyCurrencyRatesUseCase, CleanupIdempotencyKeysUseCase];
