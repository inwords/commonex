import {IntersectionType} from '@nestjs/swagger';

import {
  CreateExpenseParamsDto,
  CreateExpenseRequestV1Dto,
  CreateExpenseRequestV2Dto,
} from '#api/http/user/dto/create-expense.dto';

// gRPC handlers receive one message, so params and body DTOs are merged. IntersectionType keeps the class-validator
// metadata of both sources, which a TypeScript intersection type would not (decorators see it as Object).
export class CreateExpenseGrpcRequestDto extends IntersectionType(CreateExpenseParamsDto, CreateExpenseRequestV1Dto) {}
export class CreateExpenseV2GrpcRequestDto extends IntersectionType(
  CreateExpenseParamsDto,
  CreateExpenseRequestV2Dto,
) {}
