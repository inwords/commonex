import React from 'react';
import {observer} from 'mobx-react-lite';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {TextFieldElement, useFormContext} from 'react-hook-form-mui';

interface Props {
  disabled?: boolean;
}

export const ExchangeRateInput = observer(({disabled = false}: Props) => {
  const {watch} = useFormContext();
  const currencyId = watch('currencyId');
  const eventCurrencyId = eventStore.currentEvent?.currencyId;

  // Не показываем, если валюты совпадают
  if (!currencyId || !eventCurrencyId || currencyId === eventCurrencyId) {
    return null;
  }

  return (
    <TextFieldElement
      name="exchangeRate"
      label="Курс обмена"
      type="number"
      disabled={disabled}
      inputProps={{
        step: 0.000001,
        min: 0,
      }}
    />
  );
});
