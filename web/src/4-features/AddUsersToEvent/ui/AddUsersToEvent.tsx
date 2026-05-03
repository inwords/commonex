import {Button} from '@mui/material';
import {AddUsersToEventModal} from '@/3-widgets/AddUsersToEventModal/AddUsersToEventModal';
import {useState} from 'react';
import {useContent} from '@/6-shared/i18n/useContent';

export const AddUsersToEvent = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const content = useContent();

  return (
    <>
      <AddUsersToEventModal isOpen={isModalOpen} setIsOpen={setIsModalOpen} />

      <Button variant="outlined" onClick={() => setIsModalOpen(true)}>
        {content.AddUsers.addBtn}
      </Button>
    </>
  );
};
