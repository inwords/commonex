import {Dialog, DialogContent, DialogTitle} from '@mui/material';
import {ExpenseForm} from '@/4-features/Expense/ui/ExpenseForm';
import {expenseService} from '@/5-entities/expense/services/expense-service';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {useEffect} from 'react';

interface Props {
  isOpen: boolean;
  setIsOpen: (status: boolean) => void;
}

export const AddExpenseModal = ({isOpen, setIsOpen}: Props) => {
  // Сбрасываем splitOption при открытии модалки
  useEffect(() => {
    if (isOpen) {
      expenseService.setSplitOption('1');
    }
  }, [isOpen]);

  return (
    <Dialog open={isOpen} fullWidth={true} onClose={() => setIsOpen(false)}>
      <DialogTitle id="alert-dialog-title">Добавление траты</DialogTitle>

      <DialogContent>
        <ExpenseForm
          onSuccess={async (isOpen, d, id) => {
            setIsOpen(isOpen);
            if (eventStore.currentEvent?.pinCode) {
              await expenseService.createExpense(d, id, eventStore.currentEvent.pinCode);
            }
          }}
        />
      </DialogContent>
    </Dialog>
  );
};
