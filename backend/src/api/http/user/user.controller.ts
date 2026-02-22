import {Body, Controller, Delete, Get, HttpCode, HttpStatus, Param, Post, Query} from '@nestjs/common';
import {UserRoutes} from './user.constants';
import {ApiResponse, ApiTags} from '@nestjs/swagger';

import {CurrencyResponseDto} from './dto/get-all-currencies.dto';
import {CreateEventRequestDto, CreateEventResponseDto} from './dto/create-event.dto';
import {GetEventInfoParamsDto, GetEventInfoRequestV1Dto, GetEventInfoResponseDto} from './dto/get-event-info.dto';
import {DeleteEventParamsDto, DeleteEventRequestDto, DeleteEventResponseDto} from './dto/delete-event.dto';
import {
  AddUsersToEventParamsDto,
  AddUsersToEventRequestDto,
  AddUsersToEventResponseDto,
} from './dto/add-users-to-event.dto';
import {GetEventExpensesParamsDto, GetEventExpensesResponseDto} from './dto/get-event-expenses.dto';
import {CreateExpenseParamsDto, CreateExpenseRequestV1Dto, CreateExpenseResponseDto} from './dto/create-expense.dto';

import {GetAllCurrenciesUseCase} from '#usecases/users/get-all-currencies.usecase';
import {GetEventExpensesUseCase} from '#usecases/users/get-event-expenses.usecase';
import {SaveEventExpenseUseCase} from '#usecases/users/save-event-expense.usecase';
import {SaveEventUseCase} from '#usecases/users/save-event.usecase';
import {GetEventInfoUseCase} from '#usecases/users/get-event-info.usecase';
import {SaveUsersToEventUseCase} from '#usecases/users/save-users-to-event.usecase';
import {DeleteEventUseCase} from '#usecases/users/delete-event.usecase';
import {isError} from '#packages/result';

@Controller(UserRoutes.root)
@ApiTags('User')
export class UserController {
  constructor(
    private readonly getEventExpensesUseCase: GetEventExpensesUseCase,
    private readonly saveEventExpenseUseCase: SaveEventExpenseUseCase,
    private readonly getAllCurrenciesUseCase: GetAllCurrenciesUseCase,
    private readonly saveEventUseCase: SaveEventUseCase,
    private readonly getEventInfoUseCase: GetEventInfoUseCase,
    private readonly saveUsersToEventUseCase: SaveUsersToEventUseCase,
    private readonly deleteEventUseCase: DeleteEventUseCase,
  ) {}

  @Get(UserRoutes.getAllCurrencies)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: [CurrencyResponseDto]})
  async getAllCurrencies(): Promise<CurrencyResponseDto[]> {
    const result = await this.getAllCurrenciesUseCase.execute();

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Post(UserRoutes.createEvent)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateEventResponseDto})
  async createEvent(@Body() body: CreateEventRequestDto): Promise<CreateEventResponseDto> {
    const {users, ...event} = body;

    const result = await this.saveEventUseCase.execute({users, event});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Get(UserRoutes.getEventInfo)
  @ApiResponse({status: HttpStatus.OK, type: GetEventInfoResponseDto})
  async getEventInfo(
    @Param() {eventId}: GetEventInfoParamsDto,
    @Query() query: GetEventInfoRequestV1Dto,
  ): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoUseCase.execute({eventId, pinCode: query.pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Delete(UserRoutes.deleteEvent)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: DeleteEventResponseDto})
  async deleteEvent(
    @Param() {eventId}: DeleteEventParamsDto,
    @Body() body: DeleteEventRequestDto,
  ): Promise<DeleteEventResponseDto> {
    const result = await this.deleteEventUseCase.execute({eventId, pinCode: body.pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Post(UserRoutes.addUsersToEvent)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: [AddUsersToEventResponseDto]})
  async addUserToEvent(
    @Param() {eventId}: AddUsersToEventParamsDto,
    @Body() body: AddUsersToEventRequestDto,
  ): Promise<AddUsersToEventResponseDto[]> {
    const result = await this.saveUsersToEventUseCase.execute({eventId, ...body});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Get(UserRoutes.getAllEventExpenses)
  @ApiResponse({status: HttpStatus.OK, type: [GetEventExpensesResponseDto]})
  async getAllEventExpenses(@Param() {eventId}: GetEventExpensesParamsDto): Promise<GetEventExpensesResponseDto[]> {
    const result = await this.getEventExpensesUseCase.execute({eventId});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @Post(UserRoutes.createExpense)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateExpenseResponseDto})
  async createExpense(
    @Body() expense: CreateExpenseRequestV1Dto,
    @Param() {eventId}: CreateExpenseParamsDto,
  ): Promise<CreateExpenseResponseDto> {
    const result = await this.saveEventExpenseUseCase.execute({
      ...expense,
      eventId,
      isCustomRate: false,
      splitInformation: expense.splitInformation.map(({userId, amount}) => ({userId, amount, exchangedAmount: amount})),
    });

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }
}
