import {Box, Button, Card, CardActions, CardContent, Stack, Typography, Avatar} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {userStore} from '@/5-entities/user/stores/user-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';

export const DebtsList = observer(() => {
  const debts = Object.entries(expenseStore.currentUserDebts);

  if (debts.length === 0) {
    return (
      <Box display="flex" justifyContent={'center'} padding={'20px'}>
        <Typography variant="body1" color="text.secondary">
          У вас нет задолженностей
        </Typography>
      </Box>
    );
  }

  return (
    <Box display="flex" justifyContent={'center'} padding={'0 10px'}>
      <Stack minWidth={300} maxWidth={540} spacing={2} width="100%">
        {debts.map(([userId, debtAmount]) => {
          const creditorUser = userStore.users.find((u) => u.id === userId);

          if (!creditorUser) return null;

          return (
            <Card key={userId}>
              <CardContent>
                <Stack direction="row" spacing={2} alignItems="center">
                  <Avatar variant="rounded">{creditorUser.name[0]}</Avatar>

                  <Box flex={1}>
                    <Typography variant="h6">{creditorUser.name}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Ваша задолженность
                    </Typography>
                  </Box>

                  <Typography variant="h5" color="error">
                    {debtAmount.toFixed(2)} {currencyStore.getCurrencyCode(eventStore.currentEvent?.currencyId)}
                  </Typography>
                </Stack>
              </CardContent>

              <CardActions>
                <Button
                  variant="contained"
                  onClick={() => {
                    expenseStore.setCurrentExpenseRefund({
                      description: `Возврат долга для ${creditorUser.name}`,
                      amount: Number(debtAmount.toFixed(2)),
                      userWhoPaidId: userStore.currentUser?.id,
                      currencyId: eventStore.currentEvent?.currencyId,
                      userWhoReceiveId: creditorUser.id,
                    });
                    expenseStore.setIsExpenseRefundModalOpen(true);
                  }}
                >
                  Вернуть
                </Button>
              </CardActions>
            </Card>
          );
        })}
      </Stack>
    </Box>
  );
});
