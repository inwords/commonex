import {Dialog, DialogContent, DialogTitle} from '@mui/material';
import {CreateEventForm} from '@/4-features/CreateEvent/ui/CreateEventForm';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  isOpen: boolean;
  setIsOpen: (status: boolean) => void;
}

export const CreateEventModal = ({isOpen, setIsOpen}: Props) => {
  const content = useContent();

  return (
    <Dialog open={isOpen} fullWidth={true} onClose={() => setIsOpen(false)}>
      <DialogTitle id="alert-dialog-title">{content.CreateEvent.modal.title}</DialogTitle>

      <DialogContent>
        <CreateEventForm />
      </DialogContent>
    </Dialog>
  );
};
