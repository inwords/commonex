import {TextFieldElement} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

export const ExpenseRefundDescriptionInput = () => {
  const content = useContent();
  return <TextFieldElement name={'description'} label={content.AddExpenseRefund.descriptionInput} required />;
};
