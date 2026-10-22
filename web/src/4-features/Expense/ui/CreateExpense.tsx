import {observer} from 'mobx-react-lite';
import {useContent} from '@/6-shared/i18n/useContent';
import {Fab, Stack} from '@mui/material';

interface Props {
  setIsOpen: (status: boolean) => void;
}

export const CreateExpense = observer(({setIsOpen}: Props) => {
  const content = useContent();
  return (
    <Stack
      style={{position: 'fixed', bottom: '20px', right: '20px'}}
      justifyContent={'end'}
      direction={'row'}
      marginTop={'20px'}
    >
      <Fab
        data-onboarding="add-expense"
        aria-label={content.AddExpense.submit}
        color="primary"
        onClick={() => setIsOpen(true)}
      >
        <span className="material-icons-outlined">+</span>
      </Fab>
    </Stack>
  );
});
