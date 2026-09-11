import {ApiProperty} from '@nestjs/swagger';
import {IsString, Length} from 'class-validator';

export class CreateEventShareTokenParamsDto {
  @ApiProperty()
  @IsString()
  eventId!: string;
}

export class CreateEventShareTokenRequestDto {
  @ApiProperty({description: 'Event PIN code', example: '1234'})
  @IsString()
  @Length(4, 4)
  pinCode!: string;
}

export class CreateEventShareTokenResponseDto {
  @ApiProperty()
  token!: string;

  @ApiProperty()
  expiresAt!: string;
}
