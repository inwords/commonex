import * as process from 'process';
import * as dotenv from 'dotenv';
import {z} from 'zod';

dotenv.config();

const envSchema = z.object({
  POSTGRES_PORT: z.string(),
  POSTGRES_USER_NAME: z.string(),
  POSTGRES_PASSWORD: z.string(),
  POSTGRES_DATABASE: z.string(),
  POSTGRES_HOST: z.string(),
  POSTGRES_SCHEMA: z.string(),
  OPEN_EXCHANGE_RATES_API_ID: z.string(),
  DEVTOOLS_SECRET: z.string(),
  OTEL_SERVICE_NAME: z.string().default('commonex-backend'),
});

const validatedEnv = envSchema.parse(process.env);

export const config = (): {
  POSTGRES_PORT: string;
  POSTGRES_USER_NAME: string;
  POSTGRES_PASSWORD: string;
  POSTGRES_DATABASE: string;
  POSTGRES_HOST: string;
  POSTGRES_SCHEMA: string;
  POSTGRES_POOL_SIZE: number;
  POSTGRES_MASTER_TARGET_SESSION_ATTRS: string;
  POSTGRES_POOL_CONNECTION_TIMEOUT_MS: number;
  POSTGRES_POOL_IDLE_TIMEOUT_MS: number;
  OPEN_EXCHANGE_RATES_API_ID: string;
  DEVTOOLS_SECRET: string;
  OTEL_SERVICE_NAME: string;
} => {
  return {
    POSTGRES_PORT: validatedEnv.POSTGRES_PORT,
    POSTGRES_USER_NAME: validatedEnv.POSTGRES_USER_NAME,
    POSTGRES_PASSWORD: validatedEnv.POSTGRES_PASSWORD,
    POSTGRES_DATABASE: validatedEnv.POSTGRES_DATABASE,
    POSTGRES_HOST: validatedEnv.POSTGRES_HOST,
    POSTGRES_SCHEMA: validatedEnv.POSTGRES_SCHEMA,
    POSTGRES_POOL_SIZE: 5,
    POSTGRES_MASTER_TARGET_SESSION_ATTRS: 'read-write',
    POSTGRES_POOL_CONNECTION_TIMEOUT_MS: 10_000,
    POSTGRES_POOL_IDLE_TIMEOUT_MS: 10_000,
    OPEN_EXCHANGE_RATES_API_ID: validatedEnv.OPEN_EXCHANGE_RATES_API_ID,
    DEVTOOLS_SECRET: validatedEnv.DEVTOOLS_SECRET,
    OTEL_SERVICE_NAME: validatedEnv.OTEL_SERVICE_NAME,
  } as const;
};

export const env = config();
