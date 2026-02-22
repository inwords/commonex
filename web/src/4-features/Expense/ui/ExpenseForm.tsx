import {FormContainer, TextFieldElement, useFieldArray, useFormContext} from 'react-hook-form-mui';
import {Button, Stack} from '@mui/material';
import {ExpenseDescriptionInput} from '@/4-features/AddExpense/ui/ExpenseNameInput';
import {ExpenseAmountInput} from '@/4-features/AddExpense/ui/ExpenseAmountInput';
import {SelectExpenseOwner} from '@/4-features/AddExpense/ui/SelectExpenseOwner';
import React, {useEffect} from 'react';
import {SplitOptions} from '@/4-features/AddExpense/ui/SplitOption';
import {useParams} from 'react-router';
import {observer} from 'mobx-react-lite';
import {userStore} from '@/5-entities/user/stores/user-store';
import {SelectUser} from '@/5-entities/user/ui/SelectUser';
import {CreateExpenseForm} from '@/5-entities/expense/types/types';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {SelectCurrency} from '@/5-entities/currency/ui/SelectCurrency';
import {ExchangeRateInput} from '@/4-features/Expense/ui/ExchangeRateInput';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';

interface Props {
  onSuccess?: (isModalOpen: boolean, data: CreateExpenseForm, id: string) => void;
  readOnly?: boolean;
  expenseData?: Partial<CreateExpenseForm>;
}

const ExpenseFormContent = observer(({readOnly = false}: Omit<Props, 'expenseData'>) => {
  const {control, watch, setValue} = useFormContext();
  const {fields, append, remove} = useFieldArray({
    control,
    name: 'splitInformation',
  });

  const isSplitEqually = expenseStore.splitOption === '1';
  const currencyId = watch('currencyId');
  const eventCurrencyId = eventStore.currentEvent?.currencyId;

  useEffect(() => {
    if (!isSplitEqually && fields.length === 0) {
      // Добавляем одно поле при переключении на "вручную"
      append({});
    } else if (isSplitEqually && fields.length > 0) {
      // Удаляем все поля при переключении обратно на "поровну"
      for (let i = fields.length - 1; i >= 0; i--) {
        remove(i);
      }
    }
  }, [isSplitEqually]);

  // Устанавливаем автоматический курс при смене валюты (только если не readOnly)
  useEffect(() => {
    if (readOnly) return;

    if (!currencyId || !eventCurrencyId || currencyId === eventCurrencyId) {
      setValue('exchangeRate', undefined);
      return;
    }

    const autoRate = currencyStore.calculateExchangeRate(currencyId, eventCurrencyId);
    if (autoRate > 0) {
      setValue('exchangeRate', parseFloat(autoRate.toFixed(6)));
    }
  }, [currencyId, eventCurrencyId, readOnly]);

  return (
    <>
      <fieldset disabled={readOnly} style={{border: 'none', padding: 0, margin: 0}}>
        <Stack spacing={2} maxWidth={600}>
          <ExpenseDescriptionInput disabled={readOnly} />

          <ExpenseAmountInput disabled={readOnly} />

          <SelectExpenseOwner disabled={readOnly} />

          <SelectCurrency disabled={readOnly} />

          <ExchangeRateInput disabled={readOnly} />

          <SplitOptions disabled={readOnly} />

          {!isSplitEqually && (
            <>
              {fields.map((field, index) => (
                <React.Fragment key={field.id}>
                  <TextFieldElement name={`splitInformation.${index}.amount`} label={'Сумма к возврату'} required disabled={readOnly} type="number" />

                  <SelectUser label="Кто должен" name={`splitInformation.${index}.userId`} disabled={readOnly} />
                </React.Fragment>
              ))}

              <Button onClick={() => append({})} variant={'outlined'} disabled={readOnly}>
                Добавить персону
              </Button>
            </>
          )}
        </Stack>
      </fieldset>

      {!readOnly && (
        <Stack justifyContent="end" marginTop={'16px'}>
          <Button type={'submit'} variant="contained">
            Добавить трату
          </Button>
        </Stack>
      )}
    </>
  );
});

export const ExpenseForm = observer(({onSuccess, readOnly = false, expenseData}: Props) => {
  const {id} = useParams();
  const initialValues = (expenseData || {
    userWhoPaidId: userStore.currentUser?.id,
    splitOption: '1',
  }) as CreateExpenseForm;

  return (
    <FormContainer
      onSuccess={async (d) => {
        if (id && !readOnly) {
          onSuccess?.(false, d, id);
        }
      }}
      defaultValues={initialValues}
    >
      <ExpenseFormContent onSuccess={onSuccess} readOnly={readOnly} />
    </FormContainer>
  );
});
