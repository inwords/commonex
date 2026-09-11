import {ApiProperty} from '@nestjs/swagger';
import {Type} from 'class-transformer';
import {IsString, Length, ValidateNested} from 'class-validator';

import {IUserInfo} from '#domain/entities/user-info.entity';

class UserDto {
  @ApiProperty()
  @IsString()
  name!: string;
}

class UserInfo {
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

class Event {
  @ApiProperty()
  id!: string;

  @ApiProperty()
  name!: string;

  @ApiProperty()
  currencyId!: string;

  @ApiProperty()
  pinCode!: string;

  @ApiProperty()
  createdAt!: Date;

  @ApiProperty()
  updatedAt!: Date;

  @ApiProperty({nullable: true})
  deletedAt!: Date | null;
}

export class CreateEventRequestDto {
  @ApiProperty()
  @IsString()
  name!: string;

  @ApiProperty()
  @IsString()
  currencyId!: string;

  @ApiProperty({isArray: true, type: UserDto})
  @ValidateNested()
  @Type(() => UserDto)
  users!: Omit<IUserInfo, 'id' | 'eventId'>[];

  @ApiProperty()
  @IsString()
  @Length(4, 4)
  pinCode!: string;
}

export class CreateEventResponseDto extends Event {
  @ApiProperty({type: [UserInfo]})
  users!: UserInfo[];
}
