import {Box, Stack, Select, MenuItem, FormControl, InputLabel} from '@mui/material';
import {observer} from 'mobx-react-lite';
import {userStore} from '@/5-entities/user/stores/user-store';
import {AddUsersToEvent} from '@/4-features/AddUsersToEvent/ui/AddUsersToEvent';
import {useContent} from '@/6-shared/i18n/useContent';

export const SelectUserList = observer(() => {
  const content = useContent();

  if (userStore.currentUser) {
    return null;
  }

  return (
    <Box display="flex" justifyContent="center" alignItems="center" minHeight="100vh">
      <Stack direction={'column'} alignItems={'center'} spacing={2}>
        <h1>{content.SelectUser.title}</h1>

        <FormControl sx={{minWidth: 300}}>
          <InputLabel id="user-select-label">{content.SelectUser.label}</InputLabel>
          <Select
            labelId="user-select-label"
            id="user-select"
            value=""
            label={content.SelectUser.label}
            onChange={(e) => {
              const selectedUser = userStore.users.find((u) => u.id === e.target.value);
              if (selectedUser) {
                userStore.setCurrentUser(selectedUser);
              }
            }}
          >
            {userStore.users.map((u) => (
              <MenuItem key={u.id} value={u.id}>
                {u.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>

        <AddUsersToEvent />
      </Stack>
    </Box>
  );
});
