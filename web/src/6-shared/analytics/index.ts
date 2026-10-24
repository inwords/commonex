import {AnalyticsService} from './analytics-service';
import {getAnalyticsConsent, setAnalyticsConsent} from './analytics-consent';
import type {AnalyticsEvent} from './analytics-event';

let instance: AnalyticsService | undefined;

function service() {
  if (typeof window === 'undefined') return undefined;
  if (!instance) {
    const browser = window as Window & {ym?: (...args: unknown[]) => void};
    instance = new AnalyticsService(
      {
        production: process.env.NODE_ENV === 'production',
        counterId: process.env.NEXT_PUBLIC_YANDEX_METRICA_ID ?? '',
        allowedHosts: process.env.NEXT_PUBLIC_ANALYTICS_ALLOWED_HOSTS ?? '',
        hostname: window.location.hostname,
        origin: window.location.origin,
        consent: getAnalyticsConsent,
      },
      {
        now: Date.now,
        load: () =>
          new Promise<void>((resolve, reject) => {
            if (!browser.ym) {
              const ym = Object.assign(
                (...args: unknown[]) => {
                  ym.a.push(args);
                },
                {a: [] as unknown[][], l: Date.now()},
              );
              browser.ym = ym;
            }
            const script = document.createElement('script');
            script.src = 'https://mc.yandex.ru/metrika/tag.js';
            script.async = true;
            script.referrerPolicy = 'no-referrer';
            const timeout = window.setTimeout(() => {
              script.remove();
              reject(new Error('Analytics unavailable'));
            }, 30000);
            script.onload = () => {
              window.clearTimeout(timeout);
              resolve();
            };
            script.onerror = () => {
              window.clearTimeout(timeout);
              script.remove();
              reject(new Error('Analytics unavailable'));
            };
            document.head.appendChild(script);
          }),
        send: (...args) => browser.ym?.(...args),
      },
    );
  }
  return instance;
}

export const isAnalyticsConfigured = () => service()?.isConfigured() ?? false;
export const initializeAnalytics = () => service()?.initialize();
export const track = (event: AnalyticsEvent) => {
  const analytics = service();
  analytics?.initialize();
  analytics?.track(event);
};
export const pageview = (url: string, referrer: string) => service()?.pageview(url, referrer);
export const stopAnalytics = () => service()?.stop();
export function changeAnalyticsConsent(value: 'granted' | 'denied') {
  const revoking = getAnalyticsConsent() === 'granted' && value === 'denied';
  if (revoking) stopAnalytics();
  setAnalyticsConsent(value);
  if (revoking) window.location.reload();
}
export {getAnalyticsConsent, subscribeAnalyticsConsent} from './analytics-consent';
