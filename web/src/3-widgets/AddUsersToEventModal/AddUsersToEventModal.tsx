import {Dialog, DialogContent, DialogTitle} from '@mui/material';
import {AddUsersToEventForm} from '@/4-features/AddUsersToEvent/ui/AddUsersToEventForm';
import {userService} from '@/5-entities/user/services/user-service';
import {useContent} from '@/6-shared/i18n/useContent';

interface Props {
  isOpen: boolean;
  setIsOpen: (status: boolean) => void;
}

export const AddUsersToEventModal = ({isOpen, setIsOpen}: Props) => {
  const content = useContent();

  return (
    <Dialog open={isOpen} fullWidth={true} onClose={() => setIsOpen(false)}>
      <DialogTitle id="alert-dialog-title">{content.AddUsers.modalTitle}</DialogTitle>

      <DialogContent>
        <AddUsersToEventForm
          onSuccess={async (users) => {
            await userService.addUsersToEvent(users);
            setIsOpen(false);
          }}
        />
      </DialogContent>
    </Dialog>
  );
};
