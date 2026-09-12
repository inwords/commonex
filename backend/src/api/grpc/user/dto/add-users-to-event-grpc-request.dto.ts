import {IntersectionType} from '@nestjs/swagger';

import {AddUsersToEventParamsDto, AddUsersToEventRequestDto} from '#api/http/user/dto/add-users-to-event.dto';

// gRPC handlers receive one message, so params and body DTOs are merged. IntersectionType keeps the class-validator
// metadata of both sources, which a TypeScript intersection type would not (decorators see it as Object).
export class AddUsersToEventGrpcRequestDto extends IntersectionType(
  AddUsersToEventParamsDto,
  AddUsersToEventRequestDto,
) {}
