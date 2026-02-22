import {Module} from '@nestjs/common';
import {TerminusModule} from '@nestjs/terminus';
import {UserController} from './user/user.controller';
import {UserV2Controller} from './user/user-v2.controller';
import {UserV3Controller} from './user/user-v3.controller';
import {HealthController} from './health/health.controller';
import {DevtoolsController} from './devtools/devtools.controller';
import {UseCasesModule} from '#usecases/usecases.layer';

@Module({
  imports: [UseCasesModule, TerminusModule],
  controllers: [UserController, UserV2Controller, UserV3Controller, HealthController, DevtoolsController],
})
export class HttpModule {}
