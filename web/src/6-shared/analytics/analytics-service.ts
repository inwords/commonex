import {safeEventParams} from './analytics-event.ts';
import type {AnalyticsEvent} from './analytics-event.ts';

const metricaConfig = {
  counterId: '113775783',
  allowedHosts: 'commonex.ru,www.commonex.ru',
};

export class AnalyticsService {
  private active = false;
  private ready = false;
  private stopped = false;
  private firstPage = false;
  private initialized = false;
  private queue: Array<{args: unknown[]; time: number}> = [];

  private options?: {
    production: boolean;
    counterId: string;
    hostname: string;
    allowedHosts: string;
    origin: string;
    consent?: () => string;
    storage?: () => Pick<Storage, 'getItem' | 'setItem'>;
  };
  private transport: {
    load: () => Promise<void>;
    send: (...args: unknown[]) => void;
    now: () => number;
    reload?: () => void;
  };

  private consentValue: 'unknown' | 'granted' | 'denied' | undefined;
  private listeners = new Set<() => void>();
  private landing?: {url: string; referrer: string};
  private lastKey?: string;
  private previous?: string;

  constructor(config?: AnalyticsService['options'], transport?: AnalyticsService['transport']) {
    this.options = config;
    this.transport = transport ?? {
      load: () => this.loadSdk(),
      send: (...args) => (window as Window & {ym?: (...args: unknown[]) => void}).ym?.(...args),
      now: Date.now,
      reload: () => window.location.reload(),
    };
  }

  private get config(): NonNullable<AnalyticsService['options']> {
    return (
      this.options ?? {
        production: process.env.NODE_ENV === 'production',
        counterId: metricaConfig.counterId,
        allowedHosts: metricaConfig.allowedHosts,
        hostname: typeof window === 'undefined' ? '' : window.location.hostname,
        origin: typeof window === 'undefined' ? 'https://commonex.ru' : window.location.origin,
      }
    );
  }

  getConsent = (): 'unknown' | 'granted' | 'denied' => {
    if (this.consentValue !== undefined) {
      return this.consentValue;
    }
    try {
      const stored = this.storage().getItem('commonex_analytics_consent_v1');
      if (stored === null) {
        this.consentValue = 'granted';
      } else {
        this.consentValue = stored === 'granted' || stored === 'denied' ? stored : 'unknown';
      }
    } catch {
      this.consentValue = 'unknown';
    }
    return this.consentValue;
  };

  changeConsent(value: 'granted' | 'denied') {
    const revoking = this.getConsent() === 'granted' && value === 'denied';
    if (revoking) {
      this.stop();
    }
    this.consentValue = value;
    try {
      this.storage().setItem('commonex_analytics_consent_v1', value);
    } catch {
      // Keep this tab's choice when browser storage is unavailable.
    }
    this.listeners.forEach((listener) => listener());
    if (revoking) {
      this.transport.reload?.();
    }
  }

  subscribeConsent = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };

  navigate(key: string, url: string, referrer: string) {
    const origin = this.config.origin;
    this.landing ??= {
      url: sanitizeAnalyticsUrl(url, origin, true),
      referrer: sanitizeAnalyticsReferrer(referrer, origin),
    };
    if (this.currentConsent() !== 'granted' || key === this.lastKey) {
      return;
    }
    this.initialize();
    const current = sanitizeAnalyticsUrl(url, origin, false);
    if (this.lastKey === undefined) {
      this.pageview(this.landing.url, this.landing.referrer);
      if (current !== sanitizeAnalyticsUrl(this.landing.url, origin, false)) {
        this.pageview(current, this.landing.url);
      }
    } else {
      this.pageview(current, this.previous ?? '');
    }
    this.previous = current;
    this.lastKey = key;
  }

  private storage() {
    return this.config.storage?.() ?? window.localStorage;
  }

  private currentConsent() {
    return this.config.consent?.() ?? this.getConsent();
  }

  isConfigured() {
    return (
      this.config.production &&
      /^[1-9]\d*$/.test(this.config.counterId) &&
      Number.isSafeInteger(Number(this.config.counterId)) &&
      this.config.allowedHosts
        .split(',')
        .map((host) => host.trim())
        .filter(Boolean)
        .includes(this.config.hostname)
    );
  }

  initialize() {
    if (this.active || this.stopped || !this.isConfigured() || this.currentConsent() !== 'granted') {
      return;
    }
    this.active = true;
    try {
      void this.transport
        .load()
        .then(() => {
          if (!this.allowed()) {
            return;
          }
          this.ready = true;
          this.flush();
        })
        .catch(() => this.stop());
    } catch {
      this.stop();
    }
  }

  track = (event: AnalyticsEvent) => {
    this.initialize();
    if (!this.allowed()) {
      return;
    }
    try {
      const params = safeEventParams(event);
      if (params) {
        this.enqueue(['reachGoal', event.name, params]);
      }
    } catch {
      /* Analytics never changes a product operation's result. */
    }
  };

  pageview(url: string, referrer: string) {
    if (!this.allowed()) {
      return;
    }
    const safeUrl = sanitizeAnalyticsUrl(url, this.config.origin, !this.firstPage);
    const pathname = new URL(safeUrl).pathname;
    const title =
      pathname === '/event' ? 'CommonEx — Trip' : pathname === '/support' ? 'CommonEx — Support' : 'CommonEx';
    const message = {
      args: ['hit', safeUrl, {referer: sanitizeAnalyticsReferrer(referrer, this.config.origin), title}],
      time: this.transport.now(),
    };
    if (!this.firstPage) {
      this.firstPage = true;
      this.queue.unshift(message);
      this.queue = this.queue.slice(0, 50);
    } else if (this.queue.length < 50) {
      this.queue.push(message);
    }
    this.flush();
  }

  stop() {
    this.active = false;
    this.stopped = true;
    this.queue = [];
  }

  private allowed() {
    return this.active && this.currentConsent() === 'granted';
  }

  private enqueue(args: unknown[]) {
    if (this.queue.length < 50) {
      this.queue.push({args, time: this.transport.now()});
    }
    this.flush();
  }

  private flush() {
    if (!this.ready || !this.firstPage || !this.allowed()) {
      return;
    }
    if (!this.initialized) {
      const firstHit = this.queue.find((message) => message.args[0] === 'hit');
      if (!firstHit) {
        return;
      }
      const options = firstHit.args[2] as {referer: string};
      // The SDK's technical init requests also need explicit safe URL/referrer.
      this.send([
        'init',
        {
          defer: true,
          url: firstHit.args[1],
          referrer: options.referer,
          webvisor: false,
          clickmap: false,
          trackLinks: false,
          trackHash: false,
          disableYtm: true,
          sendTitle: false,
          accurateTrackBounce: false,
          ecommerce: false,
        },
      ]);
      this.initialized = true;
    }
    const messages = this.queue;
    this.queue = [];
    for (const message of messages) {
      if (this.transport.now() - message.time <= 30000) {
        this.send(message.args);
      }
    }
  }

  private send(args: unknown[]) {
    if (!this.allowed()) {
      return;
    }
    try {
      this.transport.send(Number(this.config.counterId), ...args);
    } catch {
      /* Optional SDK. */
    }
  }
  private loadSdk() {
    const browser = window as Window & {ym?: (...args: unknown[]) => void};
    return new Promise<void>((resolve, reject) => {
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
      script.src = `https://mc.yandex.ru/metrika/tag.js?id=${this.config.counterId}`;
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
    });
  }

  failureReason(error: unknown): 'network' | 'access_denied' | 'not_found' | 'validation' | 'server' | 'unknown' {
    if (error instanceof TypeError) {
      return 'network';
    }
    if (!error || typeof error !== 'object') {
      return 'unknown';
    }
    const {statusCode, code} = error as {statusCode?: number; code?: string};
    if (['B4003', 'B4008', 'B4009'].includes(code ?? '') || statusCode === 401 || statusCode === 403) {
      return 'access_denied';
    }
    if (code === 'B4001' || code === 'B4002' || statusCode === 404) {
      return 'not_found';
    }
    if (statusCode === 400 || statusCode === 422) {
      return 'validation';
    }
    if (statusCode && statusCode >= 500) {
      return 'server';
    }
    return 'unknown';
  }
}

export const analyticsService = new AnalyticsService();

const campaigns: Record<string, readonly string[]> = {
  utm_source: ['telegram', 'whatsapp', 'vk', 'yandex', 'google', 'commonex', 'qr', 'other'],
  utm_medium: ['messenger', 'social', 'cpc', 'organic', 'referral', 'qr', 'email', 'other'],
  utm_campaign: ['travel_launch', 'trip_invite', 'other'],
  utm_content: ['post', 'profile', 'qr', 'other'],
};

export function sanitizeAnalyticsUrl(rawUrl: string, origin: string, includeCampaign: boolean): string {
  const safe = new URL('/other', origin);
  try {
    const url = new URL(rawUrl, origin);
    if (url.origin !== origin) {
      return safe.href;
    }
    safe.pathname =
      /^\/event\/[^/]+\/?$/.test(url.pathname) || url.pathname === '/event'
        ? '/event'
        : ['/', '/support', '/support/'].includes(url.pathname)
          ? url.pathname.replace(/\/$/, '') || '/'
          : '/other';
    if (includeCampaign) {
      for (const [name, values] of Object.entries(campaigns)) {
        const value = url.searchParams.get(name);
        if (value) {
          safe.searchParams.set(name, values.includes(value) ? value : 'other');
        }
      }
    }
  } catch {
    // Invalid input is represented as an unknown page, never passed through.
  }
  return safe.href;
}

export function sanitizeAnalyticsReferrer(rawReferrer: string, origin: string): string {
  try {
    const url = new URL(rawReferrer);
    if (!['http:', 'https:'].includes(url.protocol)) {
      return '';
    }
    return url.origin === origin ? sanitizeAnalyticsUrl(url.href, origin, false) : `${url.origin}/`;
  } catch {
    return '';
  }
}
