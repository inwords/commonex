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
import {useContent} from '@/6-shared/i18n/useContent';

export const EventTabs = observer(() => {
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const content = useContent();

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
          <Tab label={content.EventTabs.myExpenses} value={0} />

          <Tab label={content.EventTabs.allExpenses} value={1} />

          <Tab label={content.EventTabs.myDebts} value={2} />

          <Tab label={content.EventTabs.myIncome} value={3} />
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
