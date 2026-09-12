import {IntersectionType} from '@nestjs/swagger';

import {AddUsersToEventParamsDto, AddUsersToEventRequestDto} from '#api/http/user/dto/add-users-to-event.dto';
import {
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
} from '#api/http/user/dto/create-event-share-token.dto';
import {
  CreateExpenseParamsDto,
  CreateExpenseRequestV1Dto,
  CreateExpenseRequestV2Dto,
} from '#api/http/user/dto/create-expense.dto';
import {DeleteEventParamsDto, DeleteEventRequestDto} from '#api/http/user/dto/delete-event.dto';
import {GetEventExpensesParamsDto, GetEventExpensesRequestV2Dto} from '#api/http/user/dto/get-event-expenses.dto';
import {
  GetEventInfoParamsDto,
  GetEventInfoRequestV1Dto,
  GetEventInfoRequestV2Dto,
} from '#api/http/user/dto/get-event-info.dto';

// gRPC handlers receive one message, so params and body DTOs are merged. IntersectionType keeps the class-validator
// metadata of both sources, which a TypeScript intersection type would not (decorators see it as Object).
export class GetEventInfoGrpcRequestDto extends IntersectionType(GetEventInfoParamsDto, GetEventInfoRequestV1Dto) {}
export class GetEventInfoV2GrpcRequestDto extends IntersectionType(GetEventInfoParamsDto, GetEventInfoRequestV2Dto) {}
export class DeleteEventGrpcRequestDto extends IntersectionType(DeleteEventParamsDto, DeleteEventRequestDto) {}
export class AddUsersToEventGrpcRequestDto extends IntersectionType(
  AddUsersToEventParamsDto,
  AddUsersToEventRequestDto,
) {}
export class GetEventExpensesGrpcRequestDto extends GetEventExpensesParamsDto {}
export class GetEventExpensesV2GrpcRequestDto extends IntersectionType(
  GetEventExpensesParamsDto,
  GetEventExpensesRequestV2Dto,
) {}
export class CreateExpenseGrpcRequestDto extends IntersectionType(CreateExpenseParamsDto, CreateExpenseRequestV1Dto) {}
export class CreateExpenseV2GrpcRequestDto extends IntersectionType(
  CreateExpenseParamsDto,
  CreateExpenseRequestV2Dto,
) {}
export class CreateEventShareTokenGrpcRequestDto extends IntersectionType(
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
) {}
