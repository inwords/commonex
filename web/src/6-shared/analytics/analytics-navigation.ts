import {sanitizeAnalyticsUrl, sanitizeAnalyticsReferrer} from './analytics-url.ts';

export class AnalyticsNavigation {
  private origin: string;
  private landing: string;
  private referrer: string;
  private lastKey?: string;
  private previous?: string;

  constructor(url: string, referrer: string) {
    this.origin = new URL(url).origin;
    this.landing = sanitizeAnalyticsUrl(url, this.origin, true);
    this.referrer = sanitizeAnalyticsReferrer(referrer, this.origin);
  }

  update(key: string, url: string, enabled: boolean, pageview: (url: string, referrer: string) => void) {
    if (!enabled || key === this.lastKey) return;
    const current = sanitizeAnalyticsUrl(url, this.origin, false);
    if (this.lastKey === undefined) {
      pageview(this.landing, this.referrer);
      if (current !== sanitizeAnalyticsUrl(this.landing, this.origin, false)) pageview(current, this.landing);
    } else pageview(current, this.previous ?? '');
    this.previous = current;
    this.lastKey = key;
  }
}
