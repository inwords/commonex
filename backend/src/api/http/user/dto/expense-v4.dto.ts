import {ApiProperty} from '@nestjs/swagger';
import {Type} from 'class-transformer';
import {
  ArrayNotEmpty,
  ArrayUnique,
  IsBoolean,
  IsDate,
  IsEnum,
  IsInt,
  IsNumber,
  IsString,
  Length,
  Min,
  ValidateIf,
  ValidateNested,
} from 'class-validator';

import {ExpenseType} from '#domain/entities/expense.entity';

class ExpenseSplitV4Dto {
  @ApiProperty()
  @IsString()
  userId!: string;

  @ApiProperty()
  @IsNumber({allowInfinity: false, allowNaN: false})
  amount!: number;

  @ApiProperty({required: false})
  @ValidateIf((_object, value: unknown) => value !== undefined)
  @IsNumber({allowInfinity: false, allowNaN: false})
  exchangedAmount?: number;
}

export class ExpenseV4ParamsDto {
  @ApiProperty()
  @IsString()
  eventId!: string;

  @ApiProperty()
  @IsString()
  id!: string;
}

export class GetEventExpensesV4ParamsDto {
  @ApiProperty()
  @IsString()
  eventId!: string;
}

export class ExpenseV4PinCodeDto {
  @ApiProperty({example: '1234'})
  @IsString()
  @Length(4, 4)
  pinCode!: string;
}

export class CreateExpenseV4RequestDto extends ExpenseV4PinCodeDto {
  @ApiProperty()
  @IsString()
  description!: string;

  @ApiProperty()
  @IsString()
  userWhoPaidId!: string;

  @ApiProperty()
  @IsString()
  currencyId!: string;

  @ApiProperty({enum: ExpenseType})
  @IsEnum(ExpenseType)
  expenseType!: ExpenseType;

  @ApiProperty({isArray: true, type: ExpenseSplitV4Dto})
  @ArrayNotEmpty()
  @ArrayUnique((split: ExpenseSplitV4Dto) => split.userId)
  @ValidateNested({each: true})
  @Type(() => ExpenseSplitV4Dto)
  splitInformation!: ExpenseSplitV4Dto[];

  @ApiProperty({required: false})
  @ValidateIf((_object, value: unknown) => value !== undefined)
  @IsDate()
  @Type(() => Date)
  createdAt?: Date;
}

export class ReviseExpenseV4RequestDto extends ExpenseV4PinCodeDto {
  @ApiProperty({minimum: 1})
  @IsInt()
  @Min(1)
  expectedVersion!: number;

  @ApiProperty()
  @IsString()
  description!: string;

  @ApiProperty()
  @IsString()
  userWhoPaidId!: string;

  @ApiProperty()
  @IsString()
  currencyId!: string;

  @ApiProperty({isArray: true, type: ExpenseSplitV4Dto})
  @ArrayNotEmpty()
  @ArrayUnique((split: ExpenseSplitV4Dto) => split.userId)
  @ValidateNested({each: true})
  @Type(() => ExpenseSplitV4Dto)
  splitInformation!: ExpenseSplitV4Dto[];

  @ApiProperty()
  @IsDate()
  @Type(() => Date)
  createdAt!: Date;

  @ApiProperty({required: false, description: 'Set true only when split exchanged amounts are custom.'})
  @ValidateIf((_object, value: unknown) => value !== undefined)
  @IsBoolean()
  isCustomRate?: boolean;
}

export class ExpenseV4ResponseDto {
  @ApiProperty()
  id!: string;

  @ApiProperty()
  version!: number;

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

  @ApiProperty({type: [ExpenseSplitV4Dto]})
  splitInformation!: ExpenseSplitV4Dto[];

  @ApiProperty()
  isCustomRate!: boolean;

  @ApiProperty()
  createdAt!: Date;

  @ApiProperty()
  updatedAt!: Date;
}
