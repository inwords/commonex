import {userStore} from '@/5-entities/user/stores/user-store';
import {Box, Button, Stack} from '@mui/material';
import {AddUsersToEvent} from '@/4-features/AddUsersToEvent/ui/AddUsersToEvent';
import {observer} from 'mobx-react-lite';
import {useContent} from '@/6-shared/i18n/useContent';
import copy from 'copy-to-clipboard';
import {eventStore} from '@/5-entities/event/stores/event-store';

export const EventUsers = observer(() => {
  const content = useContent();

  return (
    <Box padding={'16px'}>
      <Stack direction={'row'} justifyContent={'end'} spacing={2}>
        <AddUsersToEvent />

        <Button
          variant="outlined"
          onClick={() => {
            const location = window.location;
            copy(`${location.origin}${location.pathname}?pinCode=${eventStore.currentEvent?.pinCode}`);
          }}
        >
          {content.EventHeader.copyLink}
        </Button>
      </Stack>

      {userStore.users.map((u) => {
        return (
          <Stack key={u.id} direction={'row'} spacing={4}>
            <p>{u.name}</p>
          </Stack>
        );
      })}
    </Box>
  );
});
