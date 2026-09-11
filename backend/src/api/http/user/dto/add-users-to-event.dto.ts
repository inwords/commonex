import {ApiProperty} from '@nestjs/swagger';
import {Type} from 'class-transformer';
import {IsString, Length, ValidateNested} from 'class-validator';

import {IUserInfo} from '#domain/entities/user-info.entity';

class UserDto {
  @ApiProperty()
  @IsString()
  name!: string;
}

export class AddUsersToEventParamsDto {
  @ApiProperty()
  @IsString()
  eventId!: string;
}

export class AddUsersToEventRequestDto {
  @ApiProperty({isArray: true, type: UserDto})
  @ValidateNested()
  @Type(() => UserDto)
  users!: Omit<IUserInfo, 'id' | 'eventId'>[];

  @ApiProperty({type: String})
  @IsString()
  @Length(4, 4)
  pinCode!: string;
}

export class AddUsersToEventResponseDto {
  @ApiProperty()
  id!: string;

  @ApiProperty()
  name!: string;

  @ApiProperty()
  eventId!: string;

  @ApiProperty()
  createdAt!: Date;

  @ApiProperty()
  updatedAt!: Date;
}

export class AddUsersToEventResponseWithUsersDto {
  @ApiProperty({type: [AddUsersToEventResponseDto]})
  users!: AddUsersToEventResponseDto[];
}
