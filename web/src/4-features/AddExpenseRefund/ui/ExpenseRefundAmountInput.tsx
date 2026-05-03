import {TextFieldElement} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

export const ExpenseRefundAmountInput = () => {
  const content = useContent();
  return <TextFieldElement name={'amount'} label={content.AddExpenseRefund.amountInput} required type="number" />;
};
