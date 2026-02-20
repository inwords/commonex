import {Box, Tab, Tabs} from '@mui/material';
import {ExpensesList} from '@/3-widgets/ExpensesList/ExpensesList';
import {expenseStore} from '@/5-entities/expense/stores/expense-store';
import {expenseService} from '@/5-entities/expense/services/expense-service';
import {observer} from 'mobx-react-lite';
import {CreateExpense} from '@/4-features/Expense/ui/CreateExpense';
import {AddExpenseModal} from '@/3-widgets/AddExpenseModal/AddExpenseModal';
import {useState} from 'react';
import {AddExpenseRefundModal} from '@/3-widgets/AddExpenseRefundModal/AddExpenseRefundModal';
import {ExpenseRefundsList} from '@/3-widgets/ExpenseRefundsList/ExpenseRefundsList';
import {DebtsList} from '@/3-widgets/DebtsList';

export const EventTabs = observer(() => {
  const [isDialogOpen, setIsDialogOpen] = useState(false);

  return (
    <>
      <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
        <Tabs
          value={expenseStore.currentTab}
          onChange={(_, v) => {
            expenseService.setCurrentTab(v);
          }}
          variant="scrollable"
          scrollButtons="auto"
        >
          <Tab label="Мои траты" value={0} />

          <Tab label="Общие траты" value={1} />

          <Tab label="Мои задолжности" value={2} />

          <Tab label="Мои поступления" value={3} />
        </Tabs>
      </Box>

      {(expenseStore.currentTab === 0 || expenseStore.currentTab === 1) && (
        <>
          <CreateExpense setIsOpen={setIsDialogOpen} />

          <AddExpenseModal isOpen={isDialogOpen} setIsOpen={setIsDialogOpen} />
        </>
      )}

      {expenseStore.currentTab === 2 && <DebtsList />}

      {expenseStore.currentTab === 3 && <ExpenseRefundsList />}

      <ExpensesList />

      <AddExpenseRefundModal />
    </>
  );
});
