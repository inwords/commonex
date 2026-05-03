import {Dialog, DialogContent, DialogTitle} from '@mui/material';
import {AddExpenseFormRefund} from '@/4-features/CreateExpenseRefund/ui/AddExpenseFormRefund';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {observer} from 'mobx-react-lite';
import {useContent} from '@/6-shared/i18n/useContent';

export const AddExpenseRefundModal = observer(() => {
  const content = useContent();

  return (
    <Dialog
      open={expenseStore.isExpenseRefundModalOpen}
      fullWidth={true}
      onClose={() => expenseStore.setIsExpenseRefundModalOpen(false)}
    >
      <DialogTitle id="alert-dialog-title">{content.AddExpenseRefund.modalTitle}</DialogTitle>

      <DialogContent>
        <AddExpenseFormRefund />
      </DialogContent>
    </Dialog>
  );
});
