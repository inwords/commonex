import {Module} from '@nestjs/common';

import {CronModule} from '#api/cron/cron.module';
import {GrpcModule} from '#api/grpc/grpcModule';
import {HttpModule} from '#api/http/http.module';

@Module({
  imports: [HttpModule, GrpcModule, CronModule],
  controllers: [],
  providers: [],
})
export class ApiModule {}
