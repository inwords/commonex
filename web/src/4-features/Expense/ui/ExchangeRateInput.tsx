import React from 'react';
import {observer} from 'mobx-react-lite';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {TextFieldElement, useFormContext} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  disabled?: boolean;
  required?: boolean;
  allowEmpty?: boolean;
}

export const ExchangeRateInput = observer(({disabled = false, required = false, allowEmpty = false}: Props) => {
  const {watch} = useFormContext();
  const content = useContent();
  const currencyId = watch('currencyId');
  const eventCurrencyId = eventStore.currentEvent?.currencyId;

  // Не показываем, если валюты совпадают
  if (!currencyId || !eventCurrencyId || currencyId === eventCurrencyId) {
    return null;
  }

  return (
    <TextFieldElement
      name="exchangeRate"
      label={content.ExchangeRate.label}
      type="number"
      disabled={disabled}
      required={required && !allowEmpty}
      helperText={allowEmpty ? content.ExpenseDetails.savedRateHint : undefined}
      rules={{
        validate: (value) =>
          (allowEmpty && (value == null || String(value).trim() === '')) ||
          !required ||
          Number(value) > 0 ||
          content.Errors.validationError,
      }}
      inputProps={{
        step: 0.000001,
        min: 0,
      }}
    />
  );
});
