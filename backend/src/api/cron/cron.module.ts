import {Module} from '@nestjs/common';

import {UseCasesModule} from '#usecases/usecases.layer';

import {CurrencyRateSchedulerController} from './currency-rate-scheduler.controller';

@Module({
  imports: [UseCasesModule],
  providers: [CurrencyRateSchedulerController],
})
export class CronModule {}
