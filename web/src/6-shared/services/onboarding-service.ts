import type {AnalyticsEvent} from '../analytics/analytics-event.ts';
import {analyticsService} from '../analytics/analytics-service.ts';
import type {OnboardingHint} from '../analytics/onboarding-hint.ts';
import type {OnboardingState} from './onboarding-state';

const initialState: OnboardingState = {status: 'active', expenseEventId: null};
const storageKey = 'commonex_guided_onboarding';

export class OnboardingService {
  private state: OnboardingState | undefined;
  private listeners = new Set<() => void>();
  private storage: () => Pick<Storage, 'getItem' | 'setItem'>;
  private lastHint?: OnboardingHint;
  private source: 'automatic' | 'manual' = 'automatic';
  private started = false;
  private track: (event: AnalyticsEvent) => void;

  constructor(
    storage: () => Pick<Storage, 'getItem' | 'setItem'> = () => localStorage,
    track: (event: AnalyticsEvent) => void = analyticsService.track,
  ) {
    this.storage = storage;
    this.track = track;
  }

  getSnapshot = (): OnboardingState => {
    if (!this.state) {
      this.state = initialState;
      try {
        const parsed: unknown = JSON.parse(this.storage().getItem(storageKey) || 'null');
        if (
          parsed &&
          typeof parsed === 'object' &&
          'status' in parsed &&
          'expenseEventId' in parsed &&
          ['active', 'skipped', 'completed'].includes(String(parsed.status)) &&
          (parsed.expenseEventId === null || typeof parsed.expenseEventId === 'string')
        ) {
          this.state = parsed as OnboardingState;
        }
      } catch {
        // The guide remains usable when browser storage is unavailable.
      }
    }
    return this.state;
  };

  getServerSnapshot = () => initialState;

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  private save(state: OnboardingState) {
    this.state = state;
    try {
      this.storage().setItem(storageKey, JSON.stringify(state));
    } catch {
      // Keep progress for this session even if persistence is blocked.
    }
    this.listeners.forEach((listener) => listener());
  }

  stepShown(step: OnboardingHint) {
    if (this.getSnapshot().status !== 'active' || this.lastHint === step) {
      return;
    }
    if (!this.started) {
      this.started = true;
      this.track({name: 'onboarding_started', params: {source: this.source}});
    }
    this.lastHint = step;
    this.track({name: 'onboarding_step_viewed', params: {step}});
  }

  skip = () => {
    if (this.getSnapshot().status !== 'active') {
      return;
    }
    this.save({...this.getSnapshot(), status: 'skipped'});
    if (this.lastHint) {
      this.track({name: 'onboarding_skipped', params: {step: this.lastHint}});
    }
  };

  restart = () => {
    this.started = false;
    this.lastHint = undefined;
    this.source = 'manual';
    this.save({...initialState});
  };

  expenseSaved(eventId: string) {
    if (this.getSnapshot().status === 'active') {
      this.save({...this.getSnapshot(), expenseEventId: eventId});
    }
  }

  complete(eventId: string) {
    if (this.getSnapshot().status === 'active' && this.getSnapshot().expenseEventId === eventId) {
      this.save({...this.getSnapshot(), status: 'completed'});
      this.track({name: 'onboarding_completed'});
    }
  }
}

export const onboardingService = new OnboardingService();
