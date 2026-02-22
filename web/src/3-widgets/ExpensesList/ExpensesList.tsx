import {Box, Button, Card, CardActions, CardContent, Stack, Typography} from '@mui/material';
import {ChevronRight} from '@mui/icons-material';
import {observer} from 'mobx-react-lite';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {userStore} from '@/5-entities/user/stores/user-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';
import {ExpenseDetailsModal} from '@/3-widgets/ExpenseDetailsModal/ExpenseDetailsModal';

export const ExpensesList = observer(() => {
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
      return expenseStore.currentUserExpenses;
    }

    if (expenseStore.currentTab === 1) {
      return expenseStore.expensesToView;
    }

    return [];
  };

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

          // Вычисляем курс валюты (если трата была в другой валюте)
          const isMultiCurrency = e.currencyId !== eventStore.currentEvent?.currencyId;
          const expenseCurrencyCode = currencyStore.getCurrencyCode(e.currencyId);

          let exchangeRate = 1;
          if (isMultiCurrency && e.splitInformation.length > 0) {
            const firstSplit = e.splitInformation[0];
            if (firstSplit.amount > 0) {
              exchangeRate = firstSplit.exchangedAmount / firstSplit.amount;
            }
          }

          const handleIconClick = () => {
            // Найдем оригинальный Expense объект
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

                <Typography variant="body2" sx={{mt: 1}}>
                  Оплатил: {userStore.usersDictIdToName[e.userWhoPaidId] || 'Неизвестно'}
                </Typography>

                {isMultiCurrency && (
                  <Typography variant="body2" sx={{mt: 0.5, color: 'text.secondary'}}>
                    Валюта траты: {expenseCurrencyCode} (курс: {exchangeRate.toFixed(2)}
                    {e.isCustomRate && ' (ручной)'})
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
                        description: `Возврат за ${e.description}`,
                        amount: Number(currentUserDebt.toFixed(2)),
                        userWhoPaidId: userStore.currentUser?.id,
                        currencyId: eventStore.currentEvent?.currencyId,
                        userWhoReceiveId: e.userWhoPaidId,
                      });
                      expenseStore.setIsExpenseRefundModalOpen(true);
                    }}
                  >
                    Вернуть
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
