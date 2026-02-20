import {SelectUser} from '@/5-entities/user/ui/SelectUser';

interface Props {
  disabled?: boolean;
}

export const SelectExpenseOwner = ({disabled}: Props) => {
  return <SelectUser label="Кто оплачивал" name="userWhoPaidId" disabled={disabled} />;
};
