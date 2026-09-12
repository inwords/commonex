import {IntersectionType} from '@nestjs/swagger';

import {GetEventExpensesParamsDto, GetEventExpensesRequestV2Dto} from '#api/http/user/dto/get-event-expenses.dto';

// gRPC handlers receive one message, so params and body DTOs are merged. IntersectionType keeps the class-validator
// metadata of both sources, which a TypeScript intersection type would not (decorators see it as Object).
export class GetEventExpensesGrpcRequestDto extends GetEventExpensesParamsDto {}
export class GetEventExpensesV2GrpcRequestDto extends IntersectionType(
  GetEventExpensesParamsDto,
  GetEventExpensesRequestV2Dto,
) {}
