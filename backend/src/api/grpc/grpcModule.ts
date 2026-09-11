import {Module} from '@nestjs/common';

import {UseCasesModule} from '#usecases/usecases.layer';

import {UserController} from '#api/grpc/user/user.controller';

@Module({
  imports: [UseCasesModule],
  controllers: [UserController],
})
export class GrpcModule {}
