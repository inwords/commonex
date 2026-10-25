import {Body, Controller, Headers, HttpCode, HttpStatus, Param, Post} from '@nestjs/common';
import {ApiResponse, ApiTags} from '@nestjs/swagger';

import {
  CreateEventShareTokenV2UseCase,
  GetEventExpensesV2UseCase,
  GetEventInfoV2UseCase,
  SaveEventExpenseV2UseCase,
  SaveUsersToEventV2UseCase,
} from '#usecases/users/v2';

import {unwrapResult} from '#api/unwrap-result';

import {
  AddUsersToEventParamsDto,
  AddUsersToEventRequestDto,
  AddUsersToEventResponseDto,
} from './dto/add-users-to-event.dto';
import {
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
  CreateEventShareTokenResponseDto,
} from './dto/create-event-share-token.dto';
import {CreateExpenseParamsDto, CreateExpenseRequestV2Dto, CreateExpenseResponseDto} from './dto/create-expense.dto';
import {
  GetEventExpensesParamsDto,
  GetEventExpensesRequestV2Dto,
  GetEventExpensesResponseDto,
} from './dto/get-event-expenses.dto';
import {GetEventInfoParamsDto, GetEventInfoRequestV2Dto, GetEventInfoResponseDto} from './dto/get-event-info.dto';
import {UserV2Routes} from './user.constants';

@Controller(UserV2Routes.root)
@ApiTags('User V2')
export class UserV2Controller {
  constructor(
    private readonly getEventInfoV2UseCase: GetEventInfoV2UseCase,
    private readonly saveUsersToEventV2UseCase: SaveUsersToEventV2UseCase,
    private readonly saveEventExpenseV2UseCase: SaveEventExpenseV2UseCase,
    private readonly getEventExpensesV2UseCase: GetEventExpensesV2UseCase,
    private readonly createEventShareTokenV2UseCase: CreateEventShareTokenV2UseCase,
  ) {}

  @Post(UserV2Routes.getEventInfo)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: GetEventInfoResponseDto})
  async getEventInfo(
    @Param() {eventId}: GetEventInfoParamsDto,
    @Body() body: GetEventInfoRequestV2Dto,
    @Headers('user-agent') userAgent: string | undefined,
  ): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoV2UseCase.execute({
      userAgent,
      eventId,
      pinCode: body.pinCode,
      token: body.token,
    });

    return unwrapResult(result);
  }

  @Post(UserV2Routes.addUsersToEvent)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: [AddUsersToEventResponseDto]})
  async addUserToEvent(
    @Param() {eventId}: AddUsersToEventParamsDto,
    @Body() body: AddUsersToEventRequestDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
    @Headers('user-agent') userAgent: string | undefined,
  ): Promise<AddUsersToEventResponseDto[]> {
    const result = await this.saveUsersToEventV2UseCase.execute({
      userAgent,
      eventId,
      ...body,
      idempotencyKey,
    });

    return unwrapResult(result);
  }

  @Post(UserV2Routes.getAllEventExpenses)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: [GetEventExpensesResponseDto]})
  async getAllEventExpenses(
    @Param() {eventId}: GetEventExpensesParamsDto,
    @Body() body: GetEventExpensesRequestV2Dto,
    @Headers('user-agent') userAgent: string | undefined,
  ): Promise<GetEventExpensesResponseDto[]> {
    const result = await this.getEventExpensesV2UseCase.execute({
      userAgent,
      eventId,
      pinCode: body.pinCode,
    });

    return unwrapResult(result);
  }

  @Post(UserV2Routes.createExpense)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateExpenseResponseDto})
  async createExpense(
    @Body() expense: CreateExpenseRequestV2Dto,
    @Param() {eventId}: CreateExpenseParamsDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
    @Headers('user-agent') userAgent: string | undefined,
  ): Promise<CreateExpenseResponseDto> {
    const result = await this.saveEventExpenseV2UseCase.execute({
      userAgent,
      ...expense,
      eventId,
      idempotencyKey,
    });

    return unwrapResult(result);
  }

  @Post(UserV2Routes.createShareToken)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateEventShareTokenResponseDto})
  async createShareToken(
    @Param() {eventId}: CreateEventShareTokenParamsDto,
    @Body() body: CreateEventShareTokenRequestDto,
    @Headers('user-agent') userAgent: string | undefined,
  ): Promise<CreateEventShareTokenResponseDto> {
    const result = await this.createEventShareTokenV2UseCase.execute({
      userAgent,
      eventId,
      pinCode: body.pinCode,
    });

    return unwrapResult(result);
  }
}
