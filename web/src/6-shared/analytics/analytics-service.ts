import {safeEventParams} from './analytics-event.ts';
import type {AnalyticsEvent} from './analytics-event.ts';
import {sanitizeAnalyticsReferrer, sanitizeAnalyticsUrl} from './analytics-url.ts';

export class AnalyticsService {
  private active = false;
  private ready = false;
  private stopped = false;
  private firstPage = false;
  private initialized = false;
  private queue: Array<{args: unknown[]; time: number}> = [];

  private config: {
    production: boolean;
    counterId: string;
    hostname: string;
    allowedHosts: string;
    origin: string;
    consent: () => string;
  };
  private transport: {load: () => Promise<void>; send: (...args: unknown[]) => void; now: () => number};

  constructor(config: AnalyticsService['config'], transport: AnalyticsService['transport']) {
    this.config = config;
    this.transport = transport;
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
    if (this.active || this.stopped || !this.isConfigured() || this.config.consent() !== 'granted') return;
    this.active = true;
    try {
      void this.transport
        .load()
        .then(() => {
          if (!this.allowed()) return;
          this.ready = true;
          this.flush();
        })
        .catch(() => this.stop());
    } catch {
      this.stop();
    }
  }

  track(event: AnalyticsEvent) {
    if (!this.allowed()) return;
    try {
      const params = safeEventParams(event);
      if (params) this.enqueue(['reachGoal', event.name, params]);
    } catch {
      /* Analytics never changes a product operation's result. */
    }
  }

  pageview(url: string, referrer: string) {
    if (!this.allowed()) return;
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
    } else if (this.queue.length < 50) this.queue.push(message);
    this.flush();
  }

  stop() {
    this.active = false;
    this.stopped = true;
    this.queue = [];
  }

  private allowed() {
    return this.active && this.config.consent() === 'granted';
  }

  private enqueue(args: unknown[]) {
    if (this.queue.length < 50) this.queue.push({args, time: this.transport.now()});
    this.flush();
  }

  private flush() {
    if (!this.ready || !this.firstPage || !this.allowed()) return;
    if (!this.initialized) {
      const firstHit = this.queue.find((message) => message.args[0] === 'hit');
      if (!firstHit) return;
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
      if (this.transport.now() - message.time <= 30000) this.send(message.args);
    }
  }

  private send(args: unknown[]) {
    if (!this.allowed()) return;
    try {
      this.transport.send(Number(this.config.counterId), ...args);
    } catch {
      /* Optional SDK. */
    }
  }
}
