import {IClientVersion} from '#domain/entities/client-version';

export const parseClientVersion = (userAgent: string | undefined): IClientVersion | null => {
  const match = /^CommonEx\/(\d+) \((Android|iOS)\/[rd]\)$/.exec(userAgent ?? '');
  if (match == null) {
    return null;
  }

  const versionCode = Number(match[1]);
  const platform = match[2];
  if (!Number.isInteger(versionCode) || versionCode > 2147483647 || (platform !== 'Android' && platform !== 'iOS')) {
    return null;
  }

  return {platform, versionCode};
};
