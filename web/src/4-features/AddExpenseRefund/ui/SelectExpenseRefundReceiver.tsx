import {SelectUser} from '@/5-entities/user/ui/SelectUser';
import {useContent} from '@/6-shared/i18n/useContent';

export const SelectExpenseRefundReceiver = () => {
  const content = useContent();
  return <SelectUser label={content.AddExpenseRefund.receiverLabel} name="userWhoReceiveId" />;
};
