import {Dialog, DialogContent, DialogTitle} from '@mui/material';
import {ExpenseForm} from '@/4-features/Expense/ui/ExpenseForm';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {observer} from 'mobx-react-lite';
import {useEffect} from 'react';

export const ExpenseDetailsModal = observer(() => {
  const isOpen = expenseStore.isExpenseDetailsModalOpen;
  const expense = expenseStore.selectedExpenseForDetails;

  // Определяем тип деления: если все суммы равны - поровну ('1'), иначе - вручную ('2')
  const allAmountsEqual = expense?.splitInformation.every(
    (split) => split.amount === expense.splitInformation[0].amount,
  );

  const splitOption = allAmountsEqual ? '1' : '2';

  // Синхронизируем splitOption со стором при открытии модалки (хук должен быть до return)
  useEffect(() => {
    if (isOpen && expense) {
      expenseStore.setSplitOption(splitOption);
    }
  }, [isOpen, expense, splitOption]);

  const handleClose = () => {
    expenseStore.setIsExpenseDetailsModalOpen(false);
    expenseStore.setSelectedExpenseForDetails(null);
  };

  if (!expense) {
    return null;
  }

  const expenseFormData = {
    description: expense.description,
    userWhoPaidId: expense.userWhoPaidId,
    currencyId: expense.currencyId,
    eventId: expense.eventId,
    // Передаем splitInformation только если трата была вручную
    ...(splitOption === '2' && {
      splitInformation: expense.splitInformation.map((split) => ({
        userId: split.userId,
        amount: split.amount,
      })),
    }),
    amount: expense.splitInformation.reduce((sum, split) => sum + split.amount, 0),
    splitOption,
  };

  return (
    <Dialog open={isOpen} fullWidth={true} onClose={handleClose}>
      <DialogTitle id="expense-details-dialog-title">Детали траты</DialogTitle>

      <DialogContent>
        <ExpenseForm readOnly={true} expenseData={expenseFormData} />
      </DialogContent>
    </Dialog>
  );
});
