import {makeAutoObservable} from 'mobx';
import {Event} from '@/5-entities/event/types/types';

export class EventStore {
  currentEvent?: Event = undefined;
  entryMethod: 'created' | 'token' | 'manual' = 'created';

  setEntryMethod(value: 'created' | 'token' | 'manual') {
    this.entryMethod = value;
  }

  isCreatingEvent: boolean = false;

  constructor() {
    makeAutoObservable(this);
  }

  setCurrentEvent(event: Event) {
    this.currentEvent = event;
  }

  setIsCreatingEvent(value: boolean) {
    this.isCreatingEvent = value;
  }
}

export const eventStore = new EventStore();
