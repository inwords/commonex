import {ApiProperty} from '@nestjs/swagger';
import {IsString, Length} from 'class-validator';
import {ExpenseType} from '#domain/entities/expense.entity';

class SplitInfo {
  @ApiProperty()
  userId!: string;

  @ApiProperty()
  amount!: number;

  @ApiProperty()
  exchangedAmount!: number;
}

export class GetEventExpensesParamsDto {
  @ApiProperty()
  @IsString()
  eventId!: string;
}

export class GetEventExpensesRequestV2Dto {
  @ApiProperty({description: 'Event PIN code', example: '1234'})
  @IsString()
  @Length(4, 4)
  pinCode!: string;
}

export class GetEventExpensesResponseDto {
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
  isCustomRate!: boolean;

  @ApiProperty()
  createdAt!: Date;

  @ApiProperty()
  updatedAt!: Date;
}

export class GetEventExpensesResponseWithExpensesDto {
  @ApiProperty({type: [GetEventExpensesResponseDto]})
  expenses!: GetEventExpensesResponseDto[];
}
