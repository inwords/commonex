import _ from 'lodash';

export interface StateChanges<T extends object> {
  inserted?: T[];
  updated?: {
    where: Partial<T>;
    newData: Partial<T>;
  }[];
  deleted?: {
    where: Partial<T>;
  }[];
}

export const applyChanges = <T extends object>(state: T[], changes: StateChanges<T>): T[] => {
  let newState = [...state];

  for (const {where} of changes.deleted ?? []) {
    newState = newState.filter((item) => !_.isMatch(item, where));
  }

  for (const {where, newData} of changes.updated ?? []) {
    newState = newState.map((item) => {
      if (_.isMatch(item, where)) {
        return {
          ...item,
          ...newData,
        };
      }

      return item;
    });
  }

  return [...newState, ...(changes.inserted ?? [])];
};
