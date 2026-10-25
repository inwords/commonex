import {Module} from '@nestjs/common';
import {TerminusModule} from '@nestjs/terminus';

import {UseCasesModule} from '#usecases/usecases.layer';

import {EventProtocolGuard} from '#api/guards/event-protocol.guard';

import {DevtoolsController} from './devtools/devtools.controller';
import {HealthController} from './health/health.controller';
import {UserV2Controller} from './user/user-v2.controller';
import {UserV3Controller} from './user/user-v3.controller';
import {UserV4Controller} from './user/user-v4.controller';
import {UserController} from './user/user.controller';

@Module({
  providers: [EventProtocolGuard],
  imports: [UseCasesModule, TerminusModule],
  controllers: [
    UserController,
    UserV2Controller,
    UserV3Controller,
    UserV4Controller,
    HealthController,
    DevtoolsController,
  ],
})
export class HttpModule {}
