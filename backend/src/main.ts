import {fastifyOtelInstrumentation} from './otel';
import {HttpAdapterHost, NestFactory} from '@nestjs/core';
import {FastifyAdapter, NestFastifyApplication} from '@nestjs/platform-fastify';
import {AppModule} from './app.module';
import {ValidationPipe} from '@nestjs/common';
import {DocumentBuilder, SwaggerModule} from '@nestjs/swagger';
import {MicroserviceOptions, Transport} from '@nestjs/microservices';
import {join} from 'path';
import {BusinessErrorFilter} from '#api/http/filters/business-error.filter';
import {ValidationExceptionFilter} from '#api/http/filters/validation-exception.filter';

async function bootstrap(): Promise<void> {
  const fastifyAdapter = new FastifyAdapter({http2: true});
  await fastifyAdapter.getInstance().register(fastifyOtelInstrumentation.plugin());

  const app = await NestFactory.create<NestFastifyApplication>(AppModule, fastifyAdapter);
  const {httpAdapter} = app.get(HttpAdapterHost);

  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
      transformOptions: {
        enableImplicitConversion: true,
      },
    }),
  );

  app.useGlobalFilters(new ValidationExceptionFilter(httpAdapter), new BusinessErrorFilter(httpAdapter));

  const config = new DocumentBuilder()
    .setTitle('Expenses Swagger')
    .setVersion('0.0.1')
    .addServer('/api', 'API Server')
    .addApiKey(
      {
        type: 'apiKey',
        name: 'x-devtools-secret',
        in: 'header',
        description: 'Devtools secret for accessing devtools endpoints',
      },
      'devtools-secret',
    )
    .build();
  const document = SwaggerModule.createDocument(app, config);

  SwaggerModule.setup('swagger/api', app, document);
  app.enableCors({origin: '*'});

  app.connectMicroservice<MicroserviceOptions>({
    transport: Transport.GRPC,
    options: {
      package: 'user', // This should match the gRPC service package name
      protoPath: join(__dirname, '../expenses.proto'), // Path to your .proto file
      url: '0.0.0.0:5000',
    },
  });

  await app.startAllMicroservices();
  await app.listen(3001, '0.0.0.0');
}

bootstrap();
