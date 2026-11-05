import {Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack} from '@mui/material';
import {ExpenseForm} from '@/4-features/Expense/ui/ExpenseForm';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {expenseService} from '@/5-entities/expense/services/expense-service';
import {canEditExpense, getExpenseEditForm} from '@/5-entities/expense/lib/expense-editing';
import {observer} from 'mobx-react-lite';
import {useState} from 'react';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {useContent} from '@/6-shared/i18n/useContent';
import {ApiError, ApiErrorCode, getUserFriendlyMessage} from '@/6-shared/api/errors';

export const ExpenseDetailsModal = observer(() => {
  const content = useContent();
  const c = content.ExpenseDetails;
  const isOpen = expenseStore.isExpenseDetailsModalOpen;
  const expense = expenseStore.selectedExpenseForDetails;
  const [isEditing, setIsEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [isReloading, setIsReloading] = useState(false);
  const busy = expenseStore.isUpdatingExpense || isReloading;

  const resetMode = () => {
    setIsEditing(false);
    setError(null);
    setConflict(false);
  };
  const handleClose = () => {
    if (busy) {
      return;
    }
    resetMode();
    expenseStore.setIsExpenseDetailsModalOpen(false);
    expenseStore.setSelectedExpenseForDetails(null);
  };

  if (!expense) {
    return null;
  }

  const expenseFormData = getExpenseEditForm(expense, eventStore.currentEvent?.currencyId);
  if (!isEditing && expense.splitInformation.every((split) => split.amount === expense.splitInformation[0]?.amount)) {
    expenseFormData.splitOption = '1';
  }

  return (
    <Dialog
      open={isOpen}
      fullWidth
      onClose={handleClose}
      disableEscapeKeyDown={busy}
      aria-labelledby="expense-details-dialog-title"
    >
      <DialogTitle id="expense-details-dialog-title">{isEditing ? c.editTitle : c.modalTitle}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} paddingTop={1}>
          {error && <Alert severity="error">{error}</Alert>}
          {conflict && (
            <Alert
              severity="warning"
              action={
                <Button
                  color="inherit"
                  loading={isReloading}
                  disabled={busy}
                  onClick={async () => {
                    const pinCode = eventStore.currentEvent?.pinCode;
                    if (!pinCode) {
                      return;
                    }
                    setIsReloading(true);
                    try {
                      await expenseService.reloadExpense(expense, pinCode);
                      resetMode();
                    } catch (error) {
                      setError(getUserFriendlyMessage(error as ApiError));
                    } finally {
                      setIsReloading(false);
                    }
                  }}
                >
                  {c.reload}
                </Button>
              }
            >
              {c.conflict}
            </Alert>
          )}
          <ExpenseForm
            key={`${expense.id}:${expense.version ?? 'legacy'}:${isEditing}`}
            readOnly={!isEditing}
            editing={isEditing}
            busy={busy}
            submitDisabled={conflict}
            submitLabel={c.save}
            expenseData={expenseFormData}
            onCancel={resetMode}
            onSuccess={async (_isOpen, data) => {
              const pinCode = eventStore.currentEvent?.pinCode;
              if (!pinCode || busy || conflict) {
                return;
              }
              setError(null);
              try {
                await expenseService.updateExpense(expense, data, pinCode);
                resetMode();
              } catch (error) {
                const apiError = error as ApiError;
                if (apiError.code === ApiErrorCode.EXPENSE_VERSION_CONFLICT) {
                  setConflict(true);
                } else {
                  setError(getUserFriendlyMessage(apiError));
                }
              }
            }}
          />
        </Stack>
      </DialogContent>
      {!isEditing && (
        <DialogActions>
          <Button onClick={handleClose}>{c.close}</Button>
          {canEditExpense(eventStore.currentEvent, expense) && (
            <Button variant="contained" onClick={() => setIsEditing(true)}>
              {c.edit}
            </Button>
          )}
        </DialogActions>
      )}
    </Dialog>
  );
});
