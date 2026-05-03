import {SelectUser} from '@/5-entities/user/ui/SelectUser';
import {useContent} from '@/6-shared/i18n/useContent';

export const SelectExpenseRefundOwner = () => {
  const content = useContent();
  return <SelectUser label={content.AddExpenseRefund.ownerLabel} name="userWhoPaidId" />;
};
