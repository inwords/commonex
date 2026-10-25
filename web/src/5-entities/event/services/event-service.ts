import {analyticsService} from '@/6-shared/analytics/analytics-service';
import type {AnalyticsEvent} from '@/6-shared/analytics/analytics-event';
import {
  createEvent,
  getEventInfo as getEventInfoApi,
  createEventShareToken as createEventShareTokenApi,
} from '@/5-entities/event/services/api';
import {userStore} from '@/5-entities/user/stores/user-store';
import {CreateEvent} from '@/5-entities/event/types/types';
import {eventStore} from '@/5-entities/event/stores/event-store';
import retry from 'async-retry';
import {ulid} from 'ulid';
import {ApiError} from '@/6-shared/api/errors';

export class EventService {
  private emit: (event: AnalyticsEvent) => void;

  constructor(emit: (event: AnalyticsEvent) => void = analyticsService.track) {
    this.emit = (event) => {
      try {
        emit(event);
      } catch {
        /* Optional analytics. */
      }
    };
  }

  private createEventKey: string | null = null;
  async getEventInfo(eventId: string, params: {pinCode?: string; token?: string}, entryMethod: 'token' | 'manual') {
    this.emit({name: 'join_started', params: {entry_method: entryMethod}});
    try {
      const resp = await getEventInfoApi(eventId, params);
      userStore.setUsers(resp.users);
      eventStore.setCurrentEvent(resp);
      eventStore.setEntryMethod(entryMethod);
      this.emit({name: 'join_succeeded', params: {entry_method: entryMethod}});
    } catch (error) {
      this.emit({
        name: 'join_failed',
        params: {entry_method: entryMethod, reason: analyticsService.failureReason(error)},
      });
      throw error;
    }
  }

  async createEvent(data: CreateEvent) {
    this.createEventKey = ulid();
    eventStore.setIsCreatingEvent(true);
    try {
      const resp = await retry(
        async (bail) => {
          try {
            return await createEvent(data, this.createEventKey!);
          } catch (err) {
            const apiError = err as ApiError;
            if (apiError.statusCode && apiError.statusCode < 500) {
              bail(err as Error);
            }
            throw err;
          }
        },
        {retries: 2, factor: 2, minTimeout: 200},
      );

      userStore.setUsers(resp.users);
      eventStore.setCurrentEvent(resp);

      eventStore.setEntryMethod('created');
      this.emit({name: 'trip_created'});
      return resp.id;
    } catch (error) {
      this.emit({name: 'trip_create_failed', params: {reason: analyticsService.failureReason(error)}});
      throw error;
    } finally {
      this.createEventKey = null;
      eventStore.setIsCreatingEvent(false);
    }
  }

  async createEventShareToken(eventId: string, pinCode: string) {
    return await createEventShareTokenApi(eventId, pinCode);
  }
}

export const eventService = new EventService();
