import {Metadata} from '@grpc/grpc-js';
import {Body, Controller, UseFilters, UsePipes} from '@nestjs/common';
import {Ctx, GrpcMethod} from '@nestjs/microservices';

import {isError} from '#packages/result';

import {DeleteEventUseCase} from '#usecases/users/delete-event.usecase';
import {GetEventExpensesUseCase} from '#usecases/users/get-event-expenses.usecase';
import {GetEventInfoUseCase} from '#usecases/users/get-event-info.usecase';
import {SaveEventExpenseUseCase} from '#usecases/users/save-event-expense.usecase';
import {SaveEventUseCase} from '#usecases/users/save-event.usecase';
import {SaveUsersToEventUseCase} from '#usecases/users/save-users-to-event.usecase';
import {
  CreateEventShareTokenV2UseCase,
  GetEventExpensesV2UseCase,
  GetEventInfoV2UseCase,
  SaveEventExpenseV2UseCase,
  SaveUsersToEventV2UseCase,
} from '#usecases/users/v2';

import {AddUsersToEventResponseWithUsersDto} from '#api/http/user/dto/add-users-to-event.dto';
import {CreateEventShareTokenResponseDto} from '#api/http/user/dto/create-event-share-token.dto';
import {CreateEventRequestDto, CreateEventResponseDto} from '#api/http/user/dto/create-event.dto';
import {GetEventInfoResponseDto} from '#api/http/user/dto/get-event-info.dto';
import {createValidationPipe} from '#api/validation-pipe';

import {GrpcBusinessErrorFilter} from '../filters/grpc-business-error.filter';
import {GrpcValidationErrorFilter} from '../filters/grpc-validation-error.filter';
import {AddUsersToEventGrpcRequestDto} from './dto/add-users-to-event-grpc-request.dto';
import {CreateEventShareTokenGrpcRequestDto} from './dto/create-event-share-token-grpc-request.dto';
import {CreateExpenseGrpcRequestDto, CreateExpenseV2GrpcRequestDto} from './dto/create-expense-grpc-request.dto';
import {DeleteEventGrpcRequestDto} from './dto/delete-event-grpc-request.dto';
import {
  GetEventExpensesGrpcRequestDto,
  GetEventExpensesV2GrpcRequestDto,
} from './dto/get-event-expenses-grpc-request.dto';
import {GetEventInfoGrpcRequestDto, GetEventInfoV2GrpcRequestDto} from './dto/get-event-info-grpc-request.dto';
import {
  DeleteEventGrpcResponseDto,
  ExpenseGrpcResponseDto,
  ExpensesGrpcResponseDto,
  toDeleteEventGrpcResponse,
  toExpenseGrpcResponse,
} from './dto/user-grpc-response.dto';

// The controller-scoped validation pipe runs on every decorated parameter, and it rejects a `Metadata` instance because
// that class declares no validation rules. Typing the context as `unknown` keeps its metatype `Object`, which the pipe
// skips, so the metadata is narrowed here instead.
const getIdempotencyKey = (context: unknown): string | undefined => {
  if (!(context instanceof Metadata)) {
    // A wiring regression here must not silently disable idempotency, so fail loudly instead of returning undefined.
    throw new Error('Expected gRPC Metadata as the handler context');
  }

  const value = context.get('idempotency-key')[0];

  return typeof value === 'string' ? value : undefined;
};

@Controller()
@UsePipes(createValidationPipe())
@UseFilters(GrpcValidationErrorFilter, GrpcBusinessErrorFilter)
export class UserController {
  constructor(
    private readonly getEventExpensesUseCase: GetEventExpensesUseCase,
    private readonly saveEventExpenseUseCase: SaveEventExpenseUseCase,
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

  @GrpcMethod('UserService', 'CreateEvent')
  async createEvent(@Body() body: CreateEventRequestDto, @Ctx() context: unknown): Promise<CreateEventResponseDto> {
    const {users, ...event} = body;

    const result = await this.saveEventUseCase.execute({
      users,
      event,
      idempotencyKey: getIdempotencyKey(context),
      url: 'grpc:CreateEvent',
    });

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'GetEventInfo')
  async getEventInfo(@Body() {eventId, pinCode}: GetEventInfoGrpcRequestDto): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoUseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'DeleteEvent')
  async deleteEvent(@Body() body: DeleteEventGrpcRequestDto): Promise<DeleteEventGrpcResponseDto> {
    const {eventId, pinCode} = body;
    const result = await this.deleteEventUseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return toDeleteEventGrpcResponse(result.value);
  }

  @GrpcMethod('UserService', 'AddUsersToEvent')
  async addUserToEvent(
    @Body() body: AddUsersToEventGrpcRequestDto,
    @Ctx() context: unknown,
  ): Promise<AddUsersToEventResponseWithUsersDto> {
    const {eventId, ...rest} = body;

    const result = await this.saveUsersToEventUseCase.execute({
      eventId,
      ...rest,
      idempotencyKey: getIdempotencyKey(context),
      url: 'grpc:AddUsersToEvent',
    });

    if (isError(result)) {
      throw result.error;
    }

    return {users: result.value};
  }

  @GrpcMethod('UserService', 'GetAllEventExpenses')
  async getAllEventExpenses(@Body() {eventId}: GetEventExpensesGrpcRequestDto): Promise<ExpensesGrpcResponseDto> {
    const result = await this.getEventExpensesUseCase.execute({eventId});

    if (isError(result)) {
      throw result.error;
    }

    return {expenses: result.value.map(toExpenseGrpcResponse)};
  }

  @GrpcMethod('UserService', 'CreateExpense')
  async createExpense(
    @Body() expense: CreateExpenseGrpcRequestDto,
    @Ctx() context: unknown,
  ): Promise<ExpenseGrpcResponseDto> {
    const result = await this.saveEventExpenseUseCase.execute({
      ...expense,
      isCustomRate: false,
      splitInformation: expense.splitInformation.map(({userId, amount}) => ({userId, amount, exchangedAmount: amount})),
      idempotencyKey: getIdempotencyKey(context),
      url: 'grpc:CreateExpense',
    });

    if (isError(result)) {
      throw result.error;
    }

    return toExpenseGrpcResponse(result.value);
  }

  @GrpcMethod('UserService', 'GetEventInfoV2')
  async getEventInfoV2(
    @Body() {eventId, pinCode, token}: GetEventInfoV2GrpcRequestDto,
  ): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoV2UseCase.execute({eventId, pinCode, token});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  @GrpcMethod('UserService', 'AddUsersToEventV2')
  async addUserToEventV2(
    @Body() body: AddUsersToEventGrpcRequestDto,
    @Ctx() context: unknown,
  ): Promise<AddUsersToEventResponseWithUsersDto> {
    const {eventId, ...rest} = body;

    const result = await this.saveUsersToEventV2UseCase.execute({
      eventId,
      ...rest,
      idempotencyKey: getIdempotencyKey(context),
      url: 'grpc:AddUsersToEventV2',
    });

    if (isError(result)) {
      throw result.error;
    }

    return {users: result.value};
  }

  @GrpcMethod('UserService', 'GetAllEventExpensesV2')
  async getAllEventExpensesV2(
    @Body() {eventId, pinCode}: GetEventExpensesV2GrpcRequestDto,
  ): Promise<ExpensesGrpcResponseDto> {
    const result = await this.getEventExpensesV2UseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return {expenses: result.value.map(toExpenseGrpcResponse)};
  }

  @GrpcMethod('UserService', 'CreateExpenseV2')
  async createExpenseV2(
    @Body() expense: CreateExpenseV2GrpcRequestDto,
    @Ctx() context: unknown,
  ): Promise<ExpenseGrpcResponseDto> {
    const result = await this.saveEventExpenseV2UseCase.execute({
      ...expense,
      idempotencyKey: getIdempotencyKey(context),
      url: 'grpc:CreateExpenseV2',
    });

    if (isError(result)) {
      throw result.error;
    }

    return toExpenseGrpcResponse(result.value);
  }

  @GrpcMethod('UserService', 'CreateEventShareTokenV2')
  async createEventShareTokenV2(
    @Body() {eventId, pinCode}: CreateEventShareTokenGrpcRequestDto,
  ): Promise<CreateEventShareTokenResponseDto> {
    const result = await this.createEventShareTokenV2UseCase.execute({eventId, pinCode});

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }
}
