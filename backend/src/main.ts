// The OpenTelemetry SDK starts on import and must patch Fastify and Nest before they are loaded.
// Side-effect imports are not reordered by the import sorter, so this stays first.
import './otel';

import {join} from 'path';

import {NestFactory} from '@nestjs/core';
import {FastifyAdapter, NestFastifyApplication} from '@nestjs/platform-fastify';

import {fastifyHttpMetricsPlugin} from '#frameworks/observability/fastify-http-metrics.plugin';

import {configureHttpApp, createGrpcOptions} from './app.factory';
import {AppModule} from './app.module';
import {fastifyOtelInstrumentation} from './otel';

async function bootstrap(): Promise<void> {
  const fastifyAdapter = new FastifyAdapter({http2: true});
  const fastifyInstance = fastifyAdapter.getInstance();

  await fastifyInstance.register(fastifyHttpMetricsPlugin, {
    applicationRoot: '/',
  });
  await fastifyInstance.register(fastifyOtelInstrumentation.plugin());

  const app = await NestFactory.create<NestFastifyApplication>(AppModule, fastifyAdapter);

  configureHttpApp(app);
  app.connectMicroservice(createGrpcOptions({url: '0.0.0.0:5000', protoPath: join(__dirname, '../expenses.proto')}));

  await app.startAllMicroservices();
  await app.listen(3001, '0.0.0.0');
}

bootstrap().catch((error: unknown) => {
  console.error('Failed to bootstrap the application', error);
  process.exit(1);
});
