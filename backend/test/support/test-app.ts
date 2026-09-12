import {createServer} from 'net';
import {join} from 'path';

import {FastifyAdapter, NestFastifyApplication} from '@nestjs/platform-fastify';
import {Test, TestingModuleBuilder} from '@nestjs/testing';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';

import {initOrUpdateCurrencies} from '#frameworks/frameworks.layer';
import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {truncateAllTables} from '#test-support/db';

import {configureHttpApp, createGrpcOptions} from '../../src/app.factory';
import {AppModule} from '../../src/app.module';

export const PROTO_PATH = join(__dirname, '../../src/expenses.proto');

export interface TestApp {
  app: NestFastifyApplication;
  rDataService: RelationalDataService;
  grpcUrl: string | null;
  /** Truncates every table and re-seeds the supported currencies, like a fresh boot. */
  reset: () => Promise<void>;
  close: () => Promise<void>;
}

export const getFreePort = (): Promise<number> =>
  new Promise((resolve, reject) => {
    const server = createServer();

    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();

      if (address == null || typeof address === 'string') {
        reject(new Error('Could not allocate a free port'));
        return;
      }

      server.close(() => {
        resolve(address.port);
      });
    });
  });

export const createTestApp = async ({
  configure = (builder): TestingModuleBuilder => builder,
  grpc = false,
}: {
  configure?: (builder: TestingModuleBuilder) => TestingModuleBuilder;
  grpc?: boolean;
} = {}): Promise<TestApp> => {
  const moduleRef = await configure(Test.createTestingModule({imports: [AppModule]})).compile();
  const app = moduleRef.createNestApplication<NestFastifyApplication>(new FastifyAdapter());

  configureHttpApp(app);

  let grpcUrl: string | null = null;

  if (grpc) {
    grpcUrl = `127.0.0.1:${await getFreePort()}`;
    app.connectMicroservice(createGrpcOptions({url: grpcUrl, protoPath: PROTO_PATH}));
  }

  await app.init();
  await app.getHttpAdapter().getInstance().ready();

  if (grpc) {
    await app.startAllMicroservices();
  }

  const rDataService = app.get<RelationalDataService>(RelationalDataServiceAbstract);

  return {
    app,
    rDataService,
    grpcUrl,
    reset: async (): Promise<void> => {
      await truncateAllTables(rDataService.dataSource);
      await initOrUpdateCurrencies(rDataService);
    },
    close: (): Promise<void> => app.close(),
  };
};
