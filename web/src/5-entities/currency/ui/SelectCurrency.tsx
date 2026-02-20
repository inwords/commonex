import {SelectElement} from 'react-hook-form-mui';
import {currencyStore} from "@/5-entities/currency/stores/currency-store";

interface Props {
  disabled?: boolean;
}

export const SelectCurrency = ({disabled}: Props) => {
  return (
    <SelectElement
      label="Валюта"
      name="currencyId"
      options={currencyStore.currenciesOptions}
      disabled={disabled}
    />
  );
};
