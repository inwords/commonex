export class AnalyticsConsent {
  private value: 'unknown' | 'granted' | 'denied' | undefined;
  private listeners = new Set<() => void>();
  private readonly key = 'commonex_analytics_consent_v1';

  private storage: () => Pick<Storage, 'getItem' | 'setItem'>;

  constructor(storage: () => Pick<Storage, 'getItem' | 'setItem'>) {
    this.storage = storage;
  }

  getSnapshot = (): 'unknown' | 'granted' | 'denied' => {
    if (this.value !== undefined) return this.value;
    try {
      const stored = this.storage().getItem(this.key);
      this.value = stored === 'granted' || stored === 'denied' ? stored : 'unknown';
    } catch {
      this.value = 'unknown';
    }
    return this.value;
  };

  set(value: 'granted' | 'denied') {
    this.value = value;
    try {
      this.storage().setItem(this.key, value);
    } catch {
      // Keep this tab's choice when browser storage is unavailable.
    }
    this.listeners.forEach((listener) => listener());
  }

  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
}

const consent = new AnalyticsConsent(() => window.localStorage);
export const getAnalyticsConsent = consent.getSnapshot;
export const setAnalyticsConsent = (value: 'granted' | 'denied') => consent.set(value);
export const subscribeAnalyticsConsent = consent.subscribe;
