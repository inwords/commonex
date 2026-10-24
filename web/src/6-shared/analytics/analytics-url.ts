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
    if (url.origin !== origin) return safe.href;
    safe.pathname =
      /^\/event\/[^/]+\/?$/.test(url.pathname) || url.pathname === '/event'
        ? '/event'
        : ['/', '/support', '/support/'].includes(url.pathname)
          ? url.pathname.replace(/\/$/, '') || '/'
          : '/other';
    if (includeCampaign) {
      for (const [name, values] of Object.entries(campaigns)) {
        const value = url.searchParams.get(name);
        if (value) safe.searchParams.set(name, values.includes(value) ? value : 'other');
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
    if (!['http:', 'https:'].includes(url.protocol)) return '';
    return url.origin === origin ? sanitizeAnalyticsUrl(url.href, origin, false) : `${url.origin}/`;
  } catch {
    return '';
  }
}
