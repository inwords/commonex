import {makeAutoObservable} from 'mobx';
import {Event} from "@/5-entities/event/types/types";

export class EventStore {
  currentEvent?: Event = undefined;
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
