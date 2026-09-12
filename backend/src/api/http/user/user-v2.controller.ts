import {Body, Controller, Headers, HttpCode, HttpStatus, Param, Post, Req} from '@nestjs/common';
import {ApiResponse, ApiTags} from '@nestjs/swagger';
import {FastifyRequest} from 'fastify';

import {isError} from '#packages/result';

import {
  CreateEventShareTokenV2UseCase,
  GetEventExpensesV2UseCase,
  GetEventInfoV2UseCase,
  SaveEventExpenseV2UseCase,
  SaveUsersToEventV2UseCase,
} from '#usecases/users/v2';

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
  ): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoV2UseCase.execute({eventId, pinCode: body.pinCode, token: body.token});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Post(UserV2Routes.addUsersToEvent)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: [AddUsersToEventResponseDto]})
  async addUserToEvent(
    @Param() {eventId}: AddUsersToEventParamsDto,
    @Body() body: AddUsersToEventRequestDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
    @Req() request: FastifyRequest,
  ): Promise<AddUsersToEventResponseDto[]> {
    const result = await this.saveUsersToEventV2UseCase.execute({eventId, ...body, idempotencyKey, url: request.url});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Post(UserV2Routes.getAllEventExpenses)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: [GetEventExpensesResponseDto]})
  async getAllEventExpenses(
    @Param() {eventId}: GetEventExpensesParamsDto,
    @Body() body: GetEventExpensesRequestV2Dto,
  ): Promise<GetEventExpensesResponseDto[]> {
    const result = await this.getEventExpensesV2UseCase.execute({eventId, pinCode: body.pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Post(UserV2Routes.createExpense)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateExpenseResponseDto})
  async createExpense(
    @Body() expense: CreateExpenseRequestV2Dto,
    @Param() {eventId}: CreateExpenseParamsDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
    @Req() request: FastifyRequest,
  ): Promise<CreateExpenseResponseDto> {
    const result = await this.saveEventExpenseV2UseCase.execute({
      ...expense,
      eventId,
      idempotencyKey,
      url: request.url,
    });

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Post(UserV2Routes.createShareToken)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateEventShareTokenResponseDto})
  async createShareToken(
    @Param() {eventId}: CreateEventShareTokenParamsDto,
    @Body() body: CreateEventShareTokenRequestDto,
  ): Promise<CreateEventShareTokenResponseDto> {
    const result = await this.createEventShareTokenV2UseCase.execute({eventId, pinCode: body.pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }
}
