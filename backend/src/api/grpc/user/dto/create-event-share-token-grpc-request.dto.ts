import {IntersectionType} from '@nestjs/swagger';

import {
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
} from '#api/http/user/dto/create-event-share-token.dto';

// gRPC handlers receive one message, so params and body DTOs are merged. IntersectionType keeps the class-validator
// metadata of both sources, which a TypeScript intersection type would not (decorators see it as Object).
export class CreateEventShareTokenGrpcRequestDto extends IntersectionType(
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
) {}
