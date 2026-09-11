import {Module, Provider} from '@nestjs/common';

import {FrameworksLayer} from '#frameworks/frameworks.layer';

import {allCronUseCases} from './cron';
import {allDevtoolsUseCases} from './devtools';
import {allHealthUseCases} from './health';
import {allSharedUseCases} from './shared';
import {allUsersUseCases} from './users';

const allUseCases: Provider[] = [
  ...allUsersUseCases,
  ...allCronUseCases,
  ...allHealthUseCases,
  ...allDevtoolsUseCases,
  ...allSharedUseCases,
];
@Module({
  imports: [FrameworksLayer],
  providers: [...allUseCases],
  exports: [...allUseCases],
})
export class UseCasesModule {}
