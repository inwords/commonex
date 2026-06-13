import {ApiProperty} from '@nestjs/swagger';
import {IsBoolean, IsDate, IsEnum, IsNumber, IsOptional, IsString, Length, ValidateNested} from 'class-validator';
import {Type} from 'class-transformer';

import {ExpenseType} from '#domain/entities/expense.entity';

class SplitInfoDto {
  @ApiProperty()
  @IsString()
  userId!: string;

  @ApiProperty()
  @IsNumber()
  amount!: number;

  @ApiProperty({required: false, description: 'Exchanged amount (if custom rate)'})
  @IsOptional()
  @IsNumber()
  exchangedAmount?: number;
}

class SplitInfo {
  @ApiProperty()
  userId!: string;

  @ApiProperty()
  amount!: number;

  @ApiProperty()
  exchangedAmount!: number;
}

export class CreateExpenseParamsDto {
  @ApiProperty()
  @IsString()
  eventId!: string;
}

export class CreateExpenseRequestV1Dto {
  @ApiProperty()
  @IsString()
  description!: string;

  @ApiProperty()
  @IsString()
  userWhoPaidId!: string;

  @ApiProperty()
  @IsString()
  currencyId!: string;

  @ApiProperty()
  @IsEnum(ExpenseType)
  expenseType!: ExpenseType;

  @ApiProperty({isArray: true, type: SplitInfoDto})
  @ValidateNested()
  @Type(() => SplitInfoDto)
  splitInformation!: Array<SplitInfoDto>;

  @ApiProperty({required: false, description: 'ISO String'})
  @IsOptional()
  @IsDate()
  @Type(() => Date)
  createdAt?: Date;
}

export class CreateExpenseRequestV2Dto extends CreateExpenseRequestV1Dto {
  @ApiProperty({description: 'Event PIN code', example: '1234'})
  @IsString()
  @Length(4, 4)
  pinCode!: string;

  @ApiProperty({required: false, description: 'Whether supplied exchanged amounts use a custom rate'})
  @IsOptional()
  @IsBoolean()
  isCustomRate?: boolean;

  @ApiProperty({required: false})
  @IsOptional()
  @IsString()
  revertsExpenseId?: string | null;

  @ApiProperty({required: false})
  @IsOptional()
  @IsString()
  replacesExpenseId?: string | null;
}

export class CreateExpenseResponseDto {
  @ApiProperty()
  id!: string;

  @ApiProperty()
  description!: string;

  @ApiProperty()
  userWhoPaidId!: string;

  @ApiProperty()
  currencyId!: string;

  @ApiProperty()
  eventId!: string;

  @ApiProperty({enum: ExpenseType})
  expenseType!: ExpenseType;

  @ApiProperty({type: [SplitInfo]})
  splitInformation!: SplitInfo[];

  @ApiProperty()
  createdAt!: Date;

  @ApiProperty()
  updatedAt!: Date;

  @ApiProperty({required: false})
  revertsExpenseId?: string | null;

  @ApiProperty({required: false})
  replacesExpenseId?: string | null;
}
