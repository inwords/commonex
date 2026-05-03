import {TextFieldElement} from 'react-hook-form-mui';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  disabled?: boolean;
}

export const ExpenseDescriptionInput = ({disabled}: Props) => {
  const content = useContent();
  return <TextFieldElement name={'description'} label={content.AddExpense.descriptionInput} required disabled={disabled} />;
};
