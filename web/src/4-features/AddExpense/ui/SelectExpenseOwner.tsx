import {SelectUser} from '@/5-entities/user/ui/SelectUser';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  disabled?: boolean;
}

export const SelectExpenseOwner = ({disabled}: Props) => {
  const content = useContent();
  return <SelectUser label={content.AddExpense.ownerLabel} name="userWhoPaidId" disabled={disabled} />;
};
