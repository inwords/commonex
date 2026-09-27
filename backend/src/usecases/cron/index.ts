import {CleanupExpiredDataUseCase} from './cleanup-expired-data.usecase';
import {FetchDailyCurrencyRatesUseCase} from './fetch-daily-currency-rates.usecase';

export const allCronUseCases = [FetchDailyCurrencyRatesUseCase, CleanupExpiredDataUseCase];
