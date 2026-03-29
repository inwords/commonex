import {SelectElement} from 'react-hook-form-mui';
import {observer} from 'mobx-react-lite';
import {userStore} from '@/5-entities/user/stores/user-store';
interface Props {
  name: string;
  label: string;
  disabled?: boolean;
  required?: boolean;
}

export const SelectUser = observer(({name, label, disabled, required}: Props) => {
  return <SelectElement name={name} label={label} options={userStore.usersToSelect} disabled={disabled} required={required} />;
});
