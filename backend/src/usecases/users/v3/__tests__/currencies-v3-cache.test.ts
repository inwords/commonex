import {buildCurrenciesV3VersionFromResponse, buildCurrenciesV3WeakEtag, isCurrenciesV3NotModified} from '../currencies-v3-cache';

describe('currencies-v3-cache', () => {
  it('builds a version from the ordered public currency snapshot', () => {
    const version = buildCurrenciesV3VersionFromResponse({
      rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
      currencies: [
        {
          updatedAt: new Date('2023-01-01T00:00:00Z'),
        },
        {
          updatedAt: new Date('2023-01-02T00:00:00Z'),
        },
      ],
    });

    expect(version).toEqual({
      rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
      currenciesUpdatedAt: [new Date('2023-01-01T00:00:00Z'), new Date('2023-01-02T00:00:00Z')],
    });
  });

  it('builds different etags when the ordered currency updatedAt list changes', () => {
    const firstVersion = {
      rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
      currenciesUpdatedAt: [new Date('2023-01-01T00:00:00Z'), new Date('2023-01-02T00:00:00Z')],
    };
    const secondVersion = {
      rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
      currenciesUpdatedAt: [new Date('2023-01-02T00:00:00Z'), new Date('2023-01-01T00:00:00Z')],
    };

    expect(buildCurrenciesV3WeakEtag(firstVersion)).not.toBe(buildCurrenciesV3WeakEtag(secondVersion));
  });

  it('matches weak and strong validators from If-None-Match', () => {
    const version = {
      rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
      currenciesUpdatedAt: [new Date('2023-01-01T00:00:00Z')],
    };
    const weakEtag = buildCurrenciesV3WeakEtag(version);
    const strongEquivalent = weakEtag.replace(/^W\//, '');

    expect(isCurrenciesV3NotModified(weakEtag, version)).toBe(true);
    expect(isCurrenciesV3NotModified(strongEquivalent, version)).toBe(true);
    expect(isCurrenciesV3NotModified(`"other-etag", ${strongEquivalent}`, version)).toBe(true);
  });

  it('ignores non-updatedAt currency fields when building the etag version', () => {
    const firstCurrencies = [
      {
        id: 'currency-eur',
        code: 'EUR',
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      },
      {
        id: 'currency-usd',
        code: 'USD',
        updatedAt: new Date('2023-01-02T00:00:00Z'),
      },
    ];
    const secondCurrencies = [
      {
        id: 'currency-eur-renamed',
        code: 'EUR-ALT',
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      },
      {
        id: 'currency-usd-renamed',
        code: 'USD-ALT',
        updatedAt: new Date('2023-01-02T00:00:00Z'),
      },
    ];
    const firstResponse = {
      currencies: firstCurrencies,
      rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
    };
    const secondResponse = {
      currencies: secondCurrencies,
      rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
    };

    expect(buildCurrenciesV3WeakEtag(buildCurrenciesV3VersionFromResponse(firstResponse))).toBe(
      buildCurrenciesV3WeakEtag(buildCurrenciesV3VersionFromResponse(secondResponse)),
    );
  });
});
