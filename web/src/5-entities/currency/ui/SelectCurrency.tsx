import {SelectElement} from 'react-hook-form-mui';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  disabled?: boolean;
}

export const SelectCurrency = ({disabled}: Props) => {
  const content = useContent();
  return (
    <SelectElement
      label={content.Currency.label}
      name="currencyId"
      options={currencyStore.currenciesOptions}
      disabled={disabled}
    />
  );
};
