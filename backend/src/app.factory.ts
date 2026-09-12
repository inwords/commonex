import {HttpAdapterHost} from '@nestjs/core';
import {GrpcOptions, Transport} from '@nestjs/microservices';
import {NestFastifyApplication} from '@nestjs/platform-fastify';
import {DocumentBuilder, SwaggerModule} from '@nestjs/swagger';

import {BusinessErrorFilter} from '#api/http/filters/business-error.filter';
import {ValidationExceptionFilter} from '#api/http/filters/validation-exception.filter';
import {createValidationPipe} from '#api/validation-pipe';

export const GRPC_PACKAGE = 'user';

export const configureHttpApp = (app: NestFastifyApplication): void => {
  const {httpAdapter} = app.get(HttpAdapterHost);

  app.useGlobalPipes(createValidationPipe());
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
};

export const createGrpcOptions = ({url, protoPath}: {url: string; protoPath: string}): GrpcOptions => ({
  transport: Transport.GRPC,
  options: {
    package: GRPC_PACKAGE,
    protoPath,
    url,
  },
});
