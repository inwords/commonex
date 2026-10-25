import {Module} from '@nestjs/common';

import {UseCasesModule} from '#usecases/usecases.layer';

import {UserController} from '#api/grpc/user/user.controller';
import {EventProtocolGuard} from '#api/guards/event-protocol.guard';

@Module({
  providers: [EventProtocolGuard],
  imports: [UseCasesModule],
  controllers: [UserController],
})
export class GrpcModule {}
