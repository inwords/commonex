import {Fab, Stack} from '@mui/material';

interface Props {
  setIsOpen: (status: boolean) => void;
}

export const CreateExpense = ({setIsOpen}: Props) => {
  return (
    <Stack style={{position: 'fixed', bottom: '20px', right: '20px'}} justifyContent={'end'} direction={'row'} marginTop={'20px'}>
      <Fab color="primary" onClick={() => setIsOpen(true)}>
        <span className="material-icons-outlined">+</span>
      </Fab>
    </Stack>
  );
};
