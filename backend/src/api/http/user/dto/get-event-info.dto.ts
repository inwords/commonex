import {ApiProperty} from '@nestjs/swagger';
import {IsString, Length, ValidateIf} from 'class-validator';

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

export class GetEventInfoParamsDto {
  @ApiProperty()
  @IsString()
  eventId!: string;
}

export class GetEventInfoRequestV1Dto {
  @ApiProperty()
  @IsString()
  @Length(4, 4)
  pinCode!: string;
}

export class GetEventInfoRequestV2Dto {
  @ApiProperty({
    description: 'Event PIN code. Either pinCode or token must be provided.',
    example: '1234',
    required: false,
  })
  @ValidateIf((o: GetEventInfoRequestV2Dto) => !o.token)
  @IsString()
  @Length(4, 4)
  pinCode?: string;

  @ApiProperty({
    description: 'Event share token. Either pinCode or token must be provided.',
    example: 'abc123def456',
    required: false,
  })
  @ValidateIf((o: GetEventInfoRequestV2Dto) => !o.pinCode)
  @IsString()
  token?: string;
}

export class GetEventInfoResponseDto extends Event {
  @ApiProperty({type: [UserInfo]})
  users!: UserInfo[];
}
