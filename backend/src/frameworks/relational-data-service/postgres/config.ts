import {join} from 'path';
import {DataSourceOptions} from 'typeorm';
import {allEntities} from './entities';
import {env} from '../../../config';
import {PostgresNamingStrategy} from './postgres-naming-strategy';

interface DbConnectionStringConfig {
  host: string;
  port: string;
  dbname: string;
  user: string;
  password: string;
  target_session_attrs: string;
}

export interface DbConfig extends DbConnectionStringConfig {
  poolSize: number;
  poolMinSize: number;
  connectionTimeoutMs: number;
  idleTimeoutMs: number;
  tcpKeepAlive: boolean;
  tcpKeepAliveInitialDelayMs: number;
  applicationName: string;
  statementTimeoutMs: number;
  lockTimeoutMs: number;
  idleInTransactionSessionTimeoutMs: number;
  queryTimeoutMs: number;
  schema: string;
  logging: boolean;
}

export const appDbConfig: DbConfig = {
  host: env.POSTGRES_HOST,
  port: env.POSTGRES_PORT,
  dbname: env.POSTGRES_DATABASE,
  user: env.POSTGRES_USER_NAME,
  password: env.POSTGRES_PASSWORD,
  target_session_attrs: env.POSTGRES_MASTER_TARGET_SESSION_ATTRS,
  poolSize: env.POSTGRES_POOL_SIZE,
  poolMinSize: env.POSTGRES_POOL_MIN_SIZE,
  connectionTimeoutMs: env.POSTGRES_POOL_CONNECTION_TIMEOUT_MS,
  idleTimeoutMs: env.POSTGRES_POOL_IDLE_TIMEOUT_MS,
  tcpKeepAlive: env.POSTGRES_TCP_KEEPALIVE,
  tcpKeepAliveInitialDelayMs: env.POSTGRES_TCP_KEEPALIVE_INITIAL_DELAY_MS,
  applicationName: env.POSTGRES_APPLICATION_NAME,
  statementTimeoutMs: env.POSTGRES_STATEMENT_TIMEOUT_MS,
  lockTimeoutMs: env.POSTGRES_LOCK_TIMEOUT_MS,
  idleInTransactionSessionTimeoutMs: env.POSTGRES_IDLE_IN_TRANSACTION_SESSION_TIMEOUT_MS,
  queryTimeoutMs: env.POSTGRES_QUERY_TIMEOUT_MS,
  schema: env.POSTGRES_SCHEMA,
  logging: false,
};

export const createTypeormConfigDefault = (config: DbConfig): DataSourceOptions => {
  return {
    type: 'postgres',
    host: config.host,
    port: Number(config.port),
    database: config.dbname,
    username: config.user,
    password: config.password,
    entities: allEntities,
    migrations: [join(__dirname, '../../../../migrations/**/*.{ts,js}')],
    extra: {
      max: config.poolSize,
      min: config.poolMinSize,
      connectionTimeoutMillis: config.connectionTimeoutMs,
      idleTimeoutMillis: config.idleTimeoutMs,
      keepAlive: config.tcpKeepAlive,
      keepAliveInitialDelayMillis: config.tcpKeepAliveInitialDelayMs,
      application_name: config.applicationName,
      statement_timeout: config.statementTimeoutMs,
      lock_timeout: config.lockTimeoutMs,
      idle_in_transaction_session_timeout: config.idleInTransactionSessionTimeoutMs,
      query_timeout: config.queryTimeoutMs,
    },
    namingStrategy: new PostgresNamingStrategy(),
    ...(config.schema === 'public' ? {} : {schema: config.schema}),
  };
};
