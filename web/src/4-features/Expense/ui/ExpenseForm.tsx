import {CheckboxElement, FormContainer, TextFieldElement, useFieldArray, useFormContext} from 'react-hook-form-mui';
import {Button, Stack, Typography} from '@mui/material';
import {ExpenseDescriptionInput} from '@/4-features/AddExpense/ui/ExpenseNameInput';
import {ExpenseAmountInput} from '@/4-features/AddExpense/ui/ExpenseAmountInput';
import {SelectExpenseOwner} from '@/4-features/AddExpense/ui/SelectExpenseOwner';
import React, {useEffect, useRef} from 'react';
import {SplitOptions} from '@/4-features/AddExpense/ui/SplitOption';
import {SplitByPercentageFields} from '@/4-features/AddExpense/ui/SplitByPercentageFields';
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
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  onSuccess?: (isModalOpen: boolean, data: CreateExpenseForm, id: string) => void | Promise<void>;
  readOnly?: boolean;
  expenseData?: Partial<CreateExpenseForm>;
  editing?: boolean;
  busy?: boolean;
  submitDisabled?: boolean;
  submitLabel?: string;
  onCancel?: () => void;
}

type ExpenseSplitDraft = Partial<Omit<CreateExpenseForm['splitInformation'][number], 'exchangedAmount'>>;

type ExpenseFormValues = Omit<CreateExpenseForm, 'splitInformation'> & {
  splitInformation: Array<ExpenseSplitDraft>;
};

const normalizeExpenseFormValues = ({splitInformation, ...rest}: ExpenseFormValues): CreateExpenseForm | null => {
  const normalizedSplitInformation = splitInformation.reduce<CreateExpenseForm['splitInformation']>((acc, split) => {
    if (split.userId && split.amount !== undefined) {
      acc.push({
        userId: split.userId,
        amount: split.amount,
      });
    }

    return acc;
  }, []);

  if (
    (rest.splitOption === '2' || rest.splitOption === '3') &&
    normalizedSplitInformation.length !== splitInformation.length
  ) {
    return null;
  }

  if (rest.splitOption === '3') {
    const totalPercentage = normalizedSplitInformation.reduce((sum, s) => sum + s.amount, 0);
    if (totalPercentage !== 100) {
      return null;
    }
  }

  return {
    ...rest,
    splitInformation: normalizedSplitInformation,
  };
};

const ExpenseFormContent = observer(
  ({
    readOnly = false,
    editing = false,
    busy = false,
    submitDisabled = false,
    submitLabel,
    onCancel,
    expenseData,
  }: Props) => {
    const {control, watch, setValue} = useFormContext<ExpenseFormValues>();
    const {fields, append, remove} = useFieldArray<ExpenseFormValues>({
      control,
      name: 'splitInformation',
    });
    const content = useContent();
    const c = content.AddExpense.manualSplit;

    const splitOption = watch('splitOption');
    const isSplitEqually = splitOption === '1';
    const splits = watch('splitInformation');
    const isCustomRate = watch('isCustomRate');
    const disabled = readOnly || busy;
    const currencyId = watch('currencyId');
    const eventCurrencyId = eventStore.currentEvent?.currencyId;
    const previousCurrencyId = useRef(currencyId);

    useEffect(() => {
      if (editing && splitOption === '2') {
        setValue(
          'amount',
          (splits ?? []).reduce((sum, split) => sum + (Number(split.amount) || 0), 0),
        );
      }
    }, [editing, splitOption, splits, setValue]);

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
    }, [append, fields.length, isSplitEqually, remove]);

    // Устанавливаем автоматический курс при смене валюты (только если не readOnly)
    useEffect(() => {
      if (readOnly) {
        return;
      }

      if (editing) {
        if (currencyId !== previousCurrencyId.current) {
          previousCurrencyId.current = currencyId;
          setValue('isCustomRate', false);
          setValue('exchangeRate', undefined);
        }
        return;
      }

      if (!currencyId || !eventCurrencyId || currencyId === eventCurrencyId) {
        setValue('exchangeRate', undefined);
        return;
      }

      const autoRate = currencyStore.calculateExchangeRate(currencyId, eventCurrencyId);
      if (autoRate > 0) {
        setValue('exchangeRate', Number(autoRate.toFixed(2)));
      }
    }, [currencyId, eventCurrencyId, readOnly, editing, setValue]);

    return (
      <>
        <fieldset disabled={disabled} style={{border: 'none', padding: 0, margin: 0}}>
          <Stack spacing={2} maxWidth={600}>
            <ExpenseDescriptionInput disabled={disabled} />

            <Stack data-onboarding="expense-amount">
              <ExpenseAmountInput disabled={disabled || (editing && splitOption === '2')} />
            </Stack>

            <Stack data-onboarding="expense-owner">
              <SelectExpenseOwner disabled={disabled} />
            </Stack>

            <SelectCurrency disabled={disabled} />

            {editing && currencyId !== eventCurrencyId ? (
              <>
                <CheckboxElement name="isCustomRate" label={content.ExpenseDetails.customRate} disabled={disabled} />
                {isCustomRate ? (
                  <ExchangeRateInput
                    disabled={disabled}
                    required
                    allowEmpty={expenseData?.isCustomRate === true && expenseData.exchangeRate === undefined}
                  />
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    {content.ExpenseDetails.automaticRate}
                  </Typography>
                )}
              </>
            ) : (
              <ExchangeRateInput disabled={disabled} />
            )}

            <Stack data-onboarding="expense-split">
              <SplitOptions disabled={disabled} />
            </Stack>

            {splitOption === '2' && (
              <>
                {fields.map((field, index) => (
                  <React.Fragment key={field.id}>
                    <TextFieldElement
                      name={`splitInformation.${index}.amount`}
                      label={c.amountLabel}
                      required
                      disabled={disabled}
                      type="number"
                    />

                    <SelectUser
                      label={c.whoOwesLabel}
                      name={`splitInformation.${index}.userId`}
                      disabled={disabled}
                      required
                    />

                    {fields.length > 1 && (
                      <Button
                        onClick={() => remove(index)}
                        variant="text"
                        color="error"
                        size="small"
                        disabled={disabled}
                      >
                        {c.remove}
                      </Button>
                    )}
                  </React.Fragment>
                ))}

                <Button onClick={() => append({})} variant={'outlined'} disabled={disabled}>
                  {c.addPerson}
                </Button>
              </>
            )}

            {splitOption === '3' && (
              <SplitByPercentageFields fields={fields} append={append} remove={remove} disabled={disabled} />
            )}
          </Stack>
        </fieldset>

        {!readOnly && (
          <Stack
            data-onboarding={editing ? undefined : 'expense-save'}
            direction="row"
            spacing={1}
            justifyContent="end"
            marginTop={'16px'}
          >
            {onCancel && (
              <Button onClick={onCancel} disabled={busy}>
                {content.ExpenseDetails.cancel}
              </Button>
            )}
            <Button
              type={'submit'}
              variant="contained"
              loading={busy || expenseStore.isCreatingExpense}
              disabled={submitDisabled}
            >
              {submitLabel ?? content.AddExpense.submit}
            </Button>
          </Stack>
        )}
      </>
    );
  },
);

export const ExpenseForm = observer(({onSuccess, readOnly = false, expenseData, ...options}: Props) => {
  const {id} = useParams();
  const initialValues = {
    userWhoPaidId: userStore.currentUser?.id,
    splitOption: '1',
    splitInformation: [],
    ...expenseData,
  } as CreateExpenseForm;

  return (
    <FormContainer<ExpenseFormValues>
      onSuccess={async (d) => {
        if (id && !readOnly && !options.busy && !options.submitDisabled) {
          const normalizedValues = normalizeExpenseFormValues(d);

          if (normalizedValues) {
            await onSuccess?.(false, normalizedValues, id);
          }
        }
      }}
      defaultValues={initialValues}
    >
      <ExpenseFormContent readOnly={readOnly} expenseData={expenseData} {...options} />
    </FormContainer>
  );
});
