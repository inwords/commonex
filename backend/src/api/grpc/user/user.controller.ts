import {Body, Controller} from '@nestjs/common';
import {GrpcMethod} from '@nestjs/microservices';
import {GetAllCurrenciesResponseDto} from '#api/http/user/dto/get-all-currencies.dto';
import {CreateEventRequestDto, CreateEventResponseDto} from '#api/http/user/dto/create-event.dto';
import {
  GetEventInfoParamsDto,
  GetEventInfoRequestV1Dto,
  GetEventInfoRequestV2Dto,
  GetEventInfoResponseDto,
} from '#api/http/user/dto/get-event-info.dto';
import {DeleteEventParamsDto, DeleteEventRequestDto, DeleteEventResponseDto} from '#api/http/user/dto/delete-event.dto';
import {
  AddUsersToEventParamsDto,
  AddUsersToEventRequestDto,
  AddUsersToEventResponseWithUsersDto,
} from '#api/http/user/dto/add-users-to-event.dto';
import {
  GetEventExpensesParamsDto,
  GetEventExpensesRequestV2Dto,
  GetEventExpensesResponseWithExpensesDto,
} from '#api/http/user/dto/get-event-expenses.dto';
import {
  CreateExpenseParamsDto,
  CreateExpenseRequestV1Dto,
  CreateExpenseRequestV2Dto,
  CreateExpenseResponseDto,
} from '#api/http/user/dto/create-expense.dto';
import {
  CreateEventShareTokenParamsDto,
  CreateEventShareTokenRequestDto,
  CreateEventShareTokenResponseDto,
} from '#api/http/user/dto/create-event-share-token.dto';
import {GetEventExpensesUseCase} from '#usecases/users/get-event-expenses.usecase';
import {SaveEventExpenseUseCase} from '#usecases/users/save-event-expense.usecase';
import {GetAllCurrenciesUseCase} from '#usecases/users/get-all-currencies.usecase';
import {SaveEventUseCase} from '#usecases/users/save-event.usecase';
import {GetEventInfoUseCase} from '#usecases/users/get-event-info.usecase';
import {SaveUsersToEventUseCase} from '#usecases/users/save-users-to-event.usecase';
import {DeleteEventUseCase} from '#usecases/users/delete-event.usecase';
import {
  GetEventInfoV2UseCase,
  SaveUsersToEventV2UseCase,
  SaveEventExpenseV2UseCase,
  GetEventExpensesV2UseCase,
  CreateEventShareTokenV2UseCase,
} from '#usecases/users/v2';
import {isError} from '#packages/result';

@Controller()
export class UserController {
  constructor(
    private readonly getEventExpensesUseCase: GetEventExpensesUseCase,
    private readonly saveEventExpenseUseCase: SaveEventExpenseUseCase,
    private readonly getAllCurrenciesUseCase: GetAllCurrenciesUseCase,
    private readonly saveEventUseCase: SaveEventUseCase,
    private readonly getEventInfoUseCase: GetEventInfoUseCase,
    private readonly saveUsersToEventUseCase: SaveUsersToEventUseCase,
    private readonly deleteEventUseCase: DeleteEventUseCase,
    private readonly getEventInfoV2UseCase: GetEventInfoV2UseCase,
    private readonly saveUsersToEventV2UseCase: SaveUsersToEventV2UseCase,
    private readonly saveEventExpenseV2UseCase: SaveEventExpenseV2UseCase,
    private readonly getEventExpensesV2UseCase: GetEventExpensesV2UseCase,
    private readonly createEventShareTokenV2UseCase: CreateEventShareTokenV2UseCase,
  ) {}

  @GrpcMethod('UserService', 'GetAllCurrencies')
  async getAllCurrencies(): Promise<GetAllCurrenciesResponseDto> {
    const result = await this.getAllCurrenciesUseCase.execute();

    if (isError(result)) {
      throw result.error;
    }

    return {currencies: result.value};
  }

  @GrpcMethod('UserService', 'CreateEvent')
  async createEvent(@Body() body: CreateEventRequestDto): Promise<CreateEventResponseDto> {
    const {users, ...event} = body;

    const result = await this.saveEventUseCase.execute({users, event});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'GetEventInfo')
  async getEventInfo(
    @Body() {eventId, pinCode}: GetEventInfoParamsDto & GetEventInfoRequestV1Dto,
  ): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoUseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'DeleteEvent')
  async deleteEvent(@Body() body: DeleteEventParamsDto & DeleteEventRequestDto): Promise<DeleteEventResponseDto> {
    const {eventId, pinCode} = body;
    const result = await this.deleteEventUseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'AddUsersToEvent')
  async addUserToEvent(
    @Body() body: AddUsersToEventRequestDto & AddUsersToEventParamsDto,
  ): Promise<AddUsersToEventResponseWithUsersDto> {
    const {eventId, ...rest} = body;

    const result = await this.saveUsersToEventUseCase.execute({eventId, ...rest});

    if (isError(result)) {
      throw result.error;
    }

    return {users: result.value};
  }

  @GrpcMethod('UserService', 'GetAllEventExpenses')
  async getAllEventExpenses(
    @Body() {eventId}: GetEventExpensesParamsDto,
  ): Promise<GetEventExpensesResponseWithExpensesDto> {
    const result = await this.getEventExpensesUseCase.execute({eventId});

    if (isError(result)) {
      throw result.error;
    }

    return {expenses: result.value};
  }

  @GrpcMethod('UserService', 'CreateExpense')
  async createExpense(
    @Body() expense: CreateExpenseRequestV1Dto & CreateExpenseParamsDto,
  ): Promise<CreateExpenseResponseDto> {
    const result = await this.saveEventExpenseUseCase.execute({
      ...expense,
      isCustomRate: false,
      splitInformation: expense.splitInformation.map(({userId, amount}) => ({userId, amount, exchangedAmount: amount})),
    });

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'GetEventInfoV2')
  async getEventInfoV2(
    @Body() {eventId, pinCode, token}: GetEventInfoParamsDto & GetEventInfoRequestV2Dto,
  ): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoV2UseCase.execute({eventId, pinCode, token});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'AddUsersToEventV2')
  async addUserToEventV2(
    @Body() body: AddUsersToEventRequestDto & AddUsersToEventParamsDto,
  ): Promise<AddUsersToEventResponseWithUsersDto> {
    const {eventId, ...rest} = body;

    const result = await this.saveUsersToEventV2UseCase.execute({eventId, ...rest});

    if (isError(result)) {
      throw result.error;
    }

    return {users: result.value};
  }

  @GrpcMethod('UserService', 'GetAllEventExpensesV2')
  async getAllEventExpensesV2(
    @Body() {eventId, pinCode}: GetEventExpensesParamsDto & GetEventExpensesRequestV2Dto,
  ): Promise<GetEventExpensesResponseWithExpensesDto> {
    const result = await this.getEventExpensesV2UseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return {expenses: result.value};
  }

  @GrpcMethod('UserService', 'CreateExpenseV2')
  async createExpenseV2(
    @Body() expense: CreateExpenseRequestV2Dto & CreateExpenseParamsDto,
  ): Promise<CreateExpenseResponseDto> {
    const result = await this.saveEventExpenseV2UseCase.execute(expense);

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'CreateEventShareTokenV2')
  async createEventShareTokenV2(
    @Body() {eventId, pinCode}: CreateEventShareTokenParamsDto & CreateEventShareTokenRequestDto,
  ): Promise<CreateEventShareTokenResponseDto> {
    const result = await this.createEventShareTokenV2UseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }
}
