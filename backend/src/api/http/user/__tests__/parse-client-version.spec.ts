import {parseClientVersion} from '../parse-client-version';

describe('parseClientVersion', () => {
  it.each([
    ['CommonEx/123 (Android/r)', {platform: 'Android', versionCode: 123}],
    ['CommonEx/456 (iOS/d)', {platform: 'iOS', versionCode: 456}],
    ['CommonEx/0 (Android/d)', {platform: 'Android', versionCode: 0}],
    ['CommonEx/2147483647 (iOS/r)', {platform: 'iOS', versionCode: 2147483647}],
  ])('parses the canonical client version from %s', (header, expected) => {
    expect(parseClientVersion(header)).toEqual(expected);
  });

  it.each([
    undefined,
    '',
    'Mozilla/5.0',
    'CommonEx/1.2.3 (Android/r)',
    'CommonEx/-1 (Android/r)',
    'CommonEx/2147483648 (Android/r)',
    'CommonEx/9007199254740993 (Android/r)',
    'CommonEx/123 (Web/r)',
    'CommonEx/123 (Android/x)',
    'CommonEx/123 (Android/r) extra',
  ])('ignores an unrecognized client version %s', (header) => {
    expect(parseClientVersion(header)).toBeNull();
  });
});
