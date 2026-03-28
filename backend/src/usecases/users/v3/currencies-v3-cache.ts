import {createHash} from 'node:crypto';
import {ICurrency} from '#domain/entities/currency.entity';
import {ICurrencyRate} from '#domain/entities/currency-rate.entity';

export interface CurrenciesV3Version {
  rateUpdatedAt: ICurrencyRate['updatedAt'];
  currenciesUpdatedAt: Array<ICurrency['updatedAt']>;
}

interface CacheHeadersReply {
  header: (name: string, value: string) => void;
}

const normalizeEtag = (etag: string): string => {
  return etag.trim().replace(/^W\//, '');
};

export const buildCurrenciesV3Version = ({rateUpdatedAt, currenciesUpdatedAt}: CurrenciesV3Version): CurrenciesV3Version => {
  return {
    rateUpdatedAt,
    currenciesUpdatedAt,
  };
};

export const buildCurrenciesV3VersionFromResponse = ({rateUpdatedAt, currencies}: {
  rateUpdatedAt: ICurrencyRate['updatedAt'];
  currencies: Array<Pick<ICurrency, 'updatedAt'>>;
}): CurrenciesV3Version => {
  return buildCurrenciesV3Version({
    rateUpdatedAt,
    currenciesUpdatedAt: currencies.map((currency) => currency.updatedAt),
  });
};

export const buildCurrenciesV3WeakEtag = (version: CurrenciesV3Version): string => {
  const serializedMetadata = JSON.stringify({
    version: 3,
    rateUpdatedAt: version.rateUpdatedAt.toISOString(),
    currenciesUpdatedAt: version.currenciesUpdatedAt.map((updatedAt) => updatedAt.toISOString()),
  });
  const hash = createHash('sha1').update(serializedMetadata).digest('hex');

  return `W/"currencies-v3-${hash}"`;
};

export const setCurrenciesV3CacheHeaders = (response: CacheHeadersReply, version: CurrenciesV3Version): void => {
  response.header('Cache-Control', 'private, no-cache');
  response.header('ETag', buildCurrenciesV3WeakEtag(version));
};

export const isCurrenciesV3NotModified = (ifNoneMatch: string | undefined, version: CurrenciesV3Version): boolean => {
  const currentEtag = buildCurrenciesV3WeakEtag(version);

  if (ifNoneMatch == null) {
    return false;
  }

  const trimmedHeader = ifNoneMatch.trim();
  if (trimmedHeader === '*') {
    return true;
  }

  const normalizedCurrentEtag = normalizeEtag(currentEtag);

  return trimmedHeader.split(',').some((candidate) => normalizeEtag(candidate) === normalizedCurrentEtag);
};
