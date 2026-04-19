import {User} from '@/5-entities/user/types/types';
import {action, computed, makeObservable, observable} from 'mobx';

export class UserStore {
  users: Array<User> = [];
  currentUser: User | undefined = undefined;
  isAddingUsers: boolean = false;

  constructor() {
    makeObservable(this, {
      users: observable,
      currentUser: observable,
      isAddingUsers: observable,
      usersToSelect: computed,
      setUsers: action,
      setCurrentUser: action,
      setIsAddingUsers: action,
    });
  }

  get usersToSelect() {
    return this.users.map((u) => {
      return {id: u.id, label: u.name};
    });
  }

  get usersDictIdToName() {
    return this.users.reduce<Record<string, string>>((prev, curr) => {
      prev[curr.id] = curr.name;

      return prev;
    }, {});
  }

  setUsers(users: Array<User>) {
    this.users = users;
  }

  setCurrentUser(user: User | undefined) {
    this.currentUser = user;
  }

  setIsAddingUsers(value: boolean) {
    this.isAddingUsers = value;
  }
}

export const userStore = new UserStore();
