import {Box, Button, Card, CardActions, CardContent, Stack, Typography} from '@mui/material';
import {ChevronRight} from '@mui/icons-material';
import {observer} from 'mobx-react-lite';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {userStore} from '@/5-entities/user/stores/user-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {ExpenseDetailsModal} from '@/3-widgets/ExpenseDetailsModal/ExpenseDetailsModal';
import {useContent} from '@/6-shared/i18n/useContent';
import {getExpenseCorrectionStatus, isExpenseCorrectionOperation, buildCorrectionStatusByExpenseId} from '@/5-entities/expense/lib/correction-status';
import {getExpenseExchangeRate} from '@/5-entities/expense/lib/exchange-rate';

export const ExpensesList = observer(() => {
  const content = useContent();

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${day}.${month}.${year} ${hours}:${minutes}:${seconds}`;
  };

  const getExpenses = () => {
    if (expenseStore.currentTab === 0) {
      return expenseStore.currentUserExpenses.filter((expense) => !isExpenseCorrectionOperation(expense));
    }

    if (expenseStore.currentTab === 1) {
      return expenseStore.expensesToView.filter((expense) => !isExpenseCorrectionOperation(expense));
    }

    return [];
  };

  const allOperations = [...expenseStore.expenses, ...expenseStore.expenseRefunds];
  const correctionStatuses = buildCorrectionStatusByExpenseId(allOperations, content.ExpensesList);

  return (
    <Box display="flex" justifyContent={'center'} padding={'20px 10px'}>
      <Stack minWidth={300} maxWidth={540} spacing={2} width="100%">
        {getExpenses().map((e) => {
          const currentUserDebt = e.splitInformation.reduce((prev, curr) => {
            if (curr.userId === userStore.currentUser?.id) {
              prev += +curr.exchangedAmount;
            }

            return prev;
          }, 0);

          const shouldShowReturnButton = userStore.currentUser?.id !== e.userWhoPaidId && currentUserDebt > 0;
          const correctionStatus = correctionStatuses.get(e.id) ?? null;

          const isMultiCurrency = e.currencyId !== eventStore.currentEvent?.currencyId;
          const expenseCurrencyCode = currencyStore.getCurrencyCode(e.currencyId);
          const exchangeRate = getExpenseExchangeRate(e, eventStore.currentEvent?.currencyId);

          const handleIconClick = () => {
            const originalExpense = expenseStore.expenses.find((exp) => exp.id === e.id);
            if (originalExpense) {
              expenseStore.setSelectedExpenseForDetails(originalExpense);
              expenseStore.setIsExpenseDetailsModalOpen(true);
            }
          };

          return (
            <Card key={e.id}>
              <CardContent>
                <Typography variant="h5">
                  <Stack direction="row" justifyContent={'space-between'} alignItems="center">
                    {e.description}

                    <Stack direction="row" alignItems="center" spacing={1}>
                      <div>
                        {e.amount.toFixed(2)} {currencyStore.getCurrencyCode(eventStore.currentEvent?.currencyId)}
                      </div>
                      <ChevronRight
                        onClick={handleIconClick}
                        sx={{color: 'text.secondary', cursor: 'pointer'}}
                      />
                    </Stack>
                  </Stack>
                </Typography>

                {correctionStatus && (
                  <Typography variant="body2" sx={{mt: 1, color: 'text.secondary'}}>
                    {correctionStatus}
                  </Typography>
                )}

                <Typography variant="body2" sx={{mt: 1}}>
                  {content.ExpensesList.paidBy} {userStore.usersDictIdToName[e.userWhoPaidId] || content.ExpensesList.unknown}
                </Typography>

                {isMultiCurrency && (
                  <Typography variant="body2" sx={{mt: 0.5, color: 'text.secondary'}}>
                    {content.ExpensesList.currency} {expenseCurrencyCode} (курс: {exchangeRate.toFixed(2)}
                    {e.isCustomRate && ` ${content.ExpensesList.manual}`})
                  </Typography>
                )}

                {currentUserDebt > 0 && (
                  <Typography variant="body2" sx={{mt: 0.5, color: 'primary.main', fontWeight: 'medium'}}>
                    Ваша доля: {currentUserDebt.toFixed(2)}
                    {currencyStore.getCurrencyCode(eventStore.currentEvent?.currencyId)}
                  </Typography>
                )}

                <Typography variant="body2" sx={{mt: 0.5, color: 'text.secondary'}}>
                  {formatDate(e.createdAt)}
                </Typography>
              </CardContent>

              <CardActions>
                {shouldShowReturnButton && (
                  <Button
                    variant="contained"
                    onClick={() => {
                      expenseStore.setCurrentExpenseRefund({
                        description: `${content.AddExpenseRefund.refundPrefix} ${e.description}`,
                        amount: Number(currentUserDebt.toFixed(2)),
                        userWhoPaidId: userStore.currentUser?.id,
                        currencyId: eventStore.currentEvent?.currencyId,
                        userWhoReceiveId: e.userWhoPaidId,
                      });
                      expenseStore.setIsExpenseRefundModalOpen(true);
                    }}
                  >
                    {content.ExpensesList.refund}
                  </Button>
                )}
              </CardActions>
            </Card>
          );
        })}
      </Stack>
      <ExpenseDetailsModal />
    </Box>
  );
});
