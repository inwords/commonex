import {Box, Card, CardActions, CardContent, Stack, Typography} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {currencyStore} from '@/5-entities/currency/stores/currency-store';
import {eventStore} from '@/5-entities/event/stores/event-store';

export const ExpenseRefundsList = observer(() => {
  const getExpenceRefunds = () => {
    if (expenseStore.currentTab === 3) {
      return expenseStore.currentUserExpenseRefunds;
    }

    return [];
  };

  return (
    <Box display="flex" justifyContent={'center'} padding={'0 10px'}>
      <Stack minWidth={300} maxWidth={540} spacing={2} width="100%">
        {getExpenceRefunds().map((e) => {
          return (
            <Card key={e.id}>
              <CardContent>
                <Typography variant="h5">
                  <Stack direction="row" justifyContent={'space-between'}>
                    {e.description}

                    <div>
                      {e.amount.toFixed(2)} {currencyStore.getCurrencyCode(eventStore.currentEvent?.currencyId)}
                    </div>
                  </Stack>
                </Typography>

                <Typography variant="body2">{e.createdAt}</Typography>
              </CardContent>

              <CardActions></CardActions>
            </Card>
          );
        })}
      </Stack>
    </Box>
  );
});
