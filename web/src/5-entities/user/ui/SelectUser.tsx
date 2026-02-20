import {SelectElement} from 'react-hook-form-mui';
import {observer} from 'mobx-react-lite';
import {userStore} from '@/5-entities/user/stores/user-store';
interface Props {
  name: string;
  label: string;
  disabled?: boolean;
}

export const SelectUser = observer(({name, label, disabled}: Props) => {
  return <SelectElement name={name} label={label} options={userStore.usersToSelect} disabled={disabled} />;
});
