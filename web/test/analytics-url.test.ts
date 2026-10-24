import assert from 'node:assert/strict';
import test from 'node:test';
import {sanitizeAnalyticsUrl, sanitizeAnalyticsReferrer} from '../src/6-shared/analytics/analytics-url.ts';
const origin = 'https://commonex.ru';
test('removes credentials and dynamic object IDs while retaining registered landing campaigns', () => {
  assert.equal(sanitizeAnalyticsUrl('/event/PRIVATE?token=SECRET#PIN', origin, true), `${origin}/event`);
  assert.equal(
    sanitizeAnalyticsUrl(
      '/?utm_source=telegram&utm_medium=messenger&utm_campaign=travel_launch&token=SECRET',
      origin,
      true,
    ),
    `${origin}/?utm_source=telegram&utm_medium=messenger&utm_campaign=travel_launch`,
  );
  assert.equal(
    sanitizeAnalyticsUrl('/?utm_source=PRIVATE&utm_campaign=PRIVATE&utm_term=SECRET', origin, true),
    `${origin}/?utm_source=other&utm_campaign=other`,
  );
  assert.equal(sanitizeAnalyticsUrl('/?utm_source=telegram', origin, false), `${origin}/`);
  assert.equal(sanitizeAnalyticsUrl('/private/SECRET', origin, true), `${origin}/other`);
  assert.equal(sanitizeAnalyticsUrl('https://evil.test/SECRET', origin, true), `${origin}/other`);
});
test('referrer only contains external origin or canonical internal route', () => {
  assert.equal(sanitizeAnalyticsReferrer('https://t.me/private?secret=yes', origin), 'https://t.me/');
  assert.equal(sanitizeAnalyticsReferrer(`${origin}/event/PRIVATE?token=SECRET`, origin), `${origin}/event`);
  assert.equal(sanitizeAnalyticsReferrer('javascript:SECRET', origin), '');
  assert.equal(sanitizeAnalyticsReferrer('invalid', origin), '');
});
