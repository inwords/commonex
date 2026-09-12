import {IntersectionType} from '@nestjs/swagger';

import {
  GetEventInfoParamsDto,
  GetEventInfoRequestV1Dto,
  GetEventInfoRequestV2Dto,
} from '#api/http/user/dto/get-event-info.dto';

// gRPC handlers receive one message, so params and body DTOs are merged. IntersectionType keeps the class-validator
// metadata of both sources, which a TypeScript intersection type would not (decorators see it as Object).
export class GetEventInfoGrpcRequestDto extends IntersectionType(GetEventInfoParamsDto, GetEventInfoRequestV1Dto) {}
export class GetEventInfoV2GrpcRequestDto extends IntersectionType(GetEventInfoParamsDto, GetEventInfoRequestV2Dto) {}
