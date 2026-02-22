import {httpClient} from '@/6-shared/api/http-client';

export const getCurrenciesWithRates = async () => {
  return await httpClient.request('/v3/user/currencies/all', {method: 'GET'});
};
