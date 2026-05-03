import {TextFieldElement} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  disabled?: boolean;
}

export const ExpenseAmountInput = ({disabled}: Props) => {
  const content = useContent();
  return <TextFieldElement name={'amount'} label={content.AddExpense.amountInput} required disabled={disabled} type="number" />;
};
