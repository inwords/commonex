import {Dialog, DialogContent, DialogTitle, Typography} from '@mui/material';
import {ExpenseForm} from '@/4-features/Expense/ui/ExpenseForm';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {observer} from 'mobx-react-lite';
import {useEffect} from 'react';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {useContent} from '@/6-shared/i18n/useContent';
import {getExpenseCorrectionStatus} from '@/5-entities/expense/lib/correction-status';
import {getExpenseExchangeRate} from '@/5-entities/expense/lib/exchange-rate';

export const ExpenseDetailsModal = observer(() => {
  const content = useContent();
  const isOpen = expenseStore.isExpenseDetailsModalOpen;
  const expense = expenseStore.selectedExpenseForDetails;

  const allAmountsEqual = expense?.splitInformation.every(
    (split) => split.amount === expense.splitInformation[0].amount,
  );

  const splitOption = allAmountsEqual ? '1' : '2';

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

  const correctionStatus = getExpenseCorrectionStatus(
    expense,
    [...expenseStore.expenses, ...expenseStore.expenseRefunds],
    content.ExpensesList,
  );

  const exchangeRate = getExpenseExchangeRate(expense, eventStore.currentEvent?.currencyId, {decimals: 2});
  const expenseFormData = {
    description: expense.description,
    userWhoPaidId: expense.userWhoPaidId,
    currencyId: expense.currencyId,
    eventId: expense.eventId,
    ...(splitOption === '2' && {
      splitInformation: expense.splitInformation.map((split) => ({
        userId: split.userId,
        amount: split.amount,
      })),
    }),
    amount: expense.splitInformation.reduce((sum, split) => sum + split.amount, 0),
    splitOption,
    ...(exchangeRate !== 1 && {exchangeRate}),
  };

  return (
    <Dialog open={isOpen} fullWidth={true} onClose={handleClose}>
      <DialogTitle id="expense-details-dialog-title">{content.ExpenseDetails.modalTitle}</DialogTitle>

      <DialogContent>
        {correctionStatus && (
          <Typography variant="body2" sx={{mb: 2, color: 'text.secondary'}}>
            {correctionStatus}
          </Typography>
        )}
        <ExpenseForm readOnly={true} expenseData={expenseFormData} />
      </DialogContent>
    </Dialog>
  );
});
