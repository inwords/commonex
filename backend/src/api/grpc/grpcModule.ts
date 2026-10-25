import {Module} from '@nestjs/common';

import {UseCasesModule} from '#usecases/usecases.layer';

import {UserController} from '#api/grpc/user/user.controller';
import {EventVersionGuard} from '#api/guards/event-version.guard';

@Module({
  providers: [EventVersionGuard],
  imports: [UseCasesModule],
  controllers: [UserController],
})
export class GrpcModule {}
