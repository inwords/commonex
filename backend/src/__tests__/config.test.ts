import {ZodError} from 'zod';

import {parseEnv} from '../config';

const complete = {
  POSTGRES_PORT: '5432',
  POSTGRES_USER_NAME: 'postgres',
  POSTGRES_PASSWORD: 'postgres',
  POSTGRES_DATABASE: 'database',
  POSTGRES_HOST: 'localhost',
  POSTGRES_SCHEMA: 'public',
  OPEN_EXCHANGE_RATES_API_ID: 'id',
  DEVTOOLS_SECRET: 'secret',
};

describe('parseEnv', () => {
  it('accepts a complete environment and defaults the OTel service name', () => {
    expect(parseEnv(complete)).toMatchObject({...complete, OTEL_SERVICE_NAME: 'commonex-backend'});
  });

  it('keeps an explicit OTel service name', () => {
    expect(parseEnv({...complete, OTEL_SERVICE_NAME: 'x'}).OTEL_SERVICE_NAME).toBe('x');
  });

  it('rejects a missing required variable', () => {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars -- destructured only to omit it from `incomplete`
    const {POSTGRES_HOST: _host, ...incomplete} = complete;

    expect(() => parseEnv(incomplete)).toThrow(ZodError);
  });
});
