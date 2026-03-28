import {ICurrency} from '#domain/entities/currency.entity';
import {SUPPORTED_CURRENCY_CODES} from '../../../../constants';

export const createSupportedCurrencyCodesFilter = (
  alias: string,
  parameterName = 'supportedCurrencyCodes',
): {
  condition: string;
  parameters: Record<string, ICurrency['code'][]>;
} => {
  return {
    condition: `${alias}.code IN (:...${parameterName})`,
    parameters: {[parameterName]: SUPPORTED_CURRENCY_CODES},
  };
};
