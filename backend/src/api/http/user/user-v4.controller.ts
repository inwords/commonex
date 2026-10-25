import {Body, Controller, Headers, HttpCode, HttpStatus, Param, Post, UseGuards} from '@nestjs/common';
import {ApiResponse, ApiTags} from '@nestjs/swagger';

import {GetEventExpensesV2UseCase} from '#usecases/users/v2';
import {GetExpenseHistoryV4UseCase, SaveEventExpenseV4UseCase, UpdateEventExpenseV4UseCase} from '#usecases/users/v4';

import {EventProtocolGuard} from '#api/guards/event-protocol.guard';
import {unwrapResult} from '#api/unwrap-result';

import {
  CreateExpenseV4RequestDto,
  ExpenseV4ParamsDto,
  ExpenseV4PinCodeDto,
  ExpenseV4ResponseDto,
  GetEventExpensesV4ParamsDto,
  ReviseExpenseV4RequestDto,
} from './dto/expense-v4.dto';
import {UserV4Routes} from './user.constants';

@UseGuards(EventProtocolGuard)
@Controller(UserV4Routes.root)
@ApiTags('User V4')
export class UserV4Controller {
  constructor(
    private readonly getEventExpensesV2UseCase: GetEventExpensesV2UseCase,
    private readonly saveEventExpenseV4UseCase: SaveEventExpenseV4UseCase,
    private readonly updateEventExpenseV4UseCase: UpdateEventExpenseV4UseCase,
    private readonly getExpenseHistoryV4UseCase: GetExpenseHistoryV4UseCase,
  ) {}

  @Post(UserV4Routes.getAllEventExpenses)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: [ExpenseV4ResponseDto]})
  async getAllEventExpenses(
    @Param() {eventId}: GetEventExpensesV4ParamsDto,
    @Body() {pinCode}: ExpenseV4PinCodeDto,
  ): Promise<ExpenseV4ResponseDto[]> {
    const result = await this.getEventExpensesV2UseCase.execute({eventId, pinCode});
    return unwrapResult(result);
  }

  @Post(UserV4Routes.createExpense)
  @ApiResponse({status: HttpStatus.CREATED, type: ExpenseV4ResponseDto})
  async createExpense(
    @Param() {eventId}: GetEventExpensesV4ParamsDto,
    @Body() body: CreateExpenseV4RequestDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
  ): Promise<ExpenseV4ResponseDto> {
    const result = await this.saveEventExpenseV4UseCase.execute({
      ...body,
      eventId,
      idempotencyKey,
    });
    return unwrapResult(result);
  }

  @Post(UserV4Routes.reviseExpense)
  @ApiResponse({status: HttpStatus.CREATED, type: ExpenseV4ResponseDto})
  async reviseExpense(
    @Param() {eventId, id}: ExpenseV4ParamsDto,
    @Body() body: ReviseExpenseV4RequestDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
  ): Promise<ExpenseV4ResponseDto> {
    const result = await this.updateEventExpenseV4UseCase.execute({
      ...body,
      eventId,
      id,
      idempotencyKey,
    });
    return unwrapResult(result);
  }

  @Post(UserV4Routes.getExpenseHistory)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: [ExpenseV4ResponseDto]})
  async getExpenseHistory(
    @Param() {eventId, id}: ExpenseV4ParamsDto,
    @Body() {pinCode}: ExpenseV4PinCodeDto,
  ): Promise<ExpenseV4ResponseDto[]> {
    const result = await this.getExpenseHistoryV4UseCase.execute({eventId, id, pinCode});
    return unwrapResult(result);
  }
}
