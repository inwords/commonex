import {IClientVersion} from '#domain/entities/client-version';

// Banner version bounds use PostgreSQL integer columns.
const MAX_VERSION_CODE = 2_147_483_647;

export const parseClientVersion = (userAgent: string | undefined): IClientVersion | null => {
  const match = /^CommonEx\/(\d+) \((Android|iOS)\/[rd]\)$/.exec(userAgent ?? '');
  if (match == null) {
    return null;
  }

  const versionCode = Number(match[1]);
  const platform = match[2];
  if (
    !Number.isInteger(versionCode) ||
    versionCode > MAX_VERSION_CODE ||
    (platform !== 'Android' && platform !== 'iOS')
  ) {
    return null;
  }

  return {platform, versionCode};
};
