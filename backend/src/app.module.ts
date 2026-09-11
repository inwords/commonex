import {Module} from '@nestjs/common';
import {ScheduleModule} from '@nestjs/schedule';

import {ApiModule} from '#api/api.layer';

@Module({
  imports: [ScheduleModule.forRoot(), ApiModule],
})
export class AppModule {}
