import {
  Body,
  Controller,
  Delete,
  Get,
  Header,
  Headers,
  HttpCode,
  HttpStatus,
  Param,
  Post,
  Query,
  Res,
} from '@nestjs/common';
import {ApiHeader, ApiResponse, ApiTags} from '@nestjs/swagger';

import {DeleteEventUseCase} from '#usecases/users/delete-event.usecase';
import {GetBannerUseCase} from '#usecases/users/get-banner.usecase';
import {GetEventExpensesUseCase} from '#usecases/users/get-event-expenses.usecase';
import {GetEventInfoUseCase} from '#usecases/users/get-event-info.usecase';
import {SaveEventExpenseUseCase} from '#usecases/users/save-event-expense.usecase';
import {SaveEventUseCase} from '#usecases/users/save-event.usecase';
import {SaveUsersToEventUseCase} from '#usecases/users/save-users-to-event.usecase';

import {unwrapResult} from '#api/unwrap-result';

import {
  AddUsersToEventParamsDto,
  AddUsersToEventRequestDto,
  AddUsersToEventResponseDto,
} from './dto/add-users-to-event.dto';
import {CreateEventRequestDto, CreateEventResponseDto} from './dto/create-event.dto';
import {CreateExpenseParamsDto, CreateExpenseRequestV1Dto, CreateExpenseResponseDto} from './dto/create-expense.dto';
import {DeleteEventParamsDto, DeleteEventRequestDto, DeleteEventResponseDto} from './dto/delete-event.dto';
import {GetBannerResponseDto} from './dto/get-banner.dto';
import {GetEventExpensesParamsDto, GetEventExpensesResponseDto} from './dto/get-event-expenses.dto';
import {GetEventInfoParamsDto, GetEventInfoRequestV1Dto, GetEventInfoResponseDto} from './dto/get-event-info.dto';
import {parseClientVersion} from './parse-client-version';
import {UserRoutes} from './user.constants';

interface BannerReply {
  code: (status: number) => BannerReply;
  send: (payload?: GetBannerResponseDto) => void;
}

@Controller(UserRoutes.root)
@ApiTags('User')
export class UserController {
  constructor(
    private readonly getEventExpensesUseCase: GetEventExpensesUseCase,
    private readonly saveEventExpenseUseCase: SaveEventExpenseUseCase,
    private readonly saveEventUseCase: SaveEventUseCase,
    private readonly getEventInfoUseCase: GetEventInfoUseCase,
    private readonly saveUsersToEventUseCase: SaveUsersToEventUseCase,
    private readonly deleteEventUseCase: DeleteEventUseCase,
    private readonly getBannerUseCase: GetBannerUseCase,
  ) {}

  @Post(UserRoutes.createEvent)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateEventResponseDto})
  async createEvent(
    @Body() body: CreateEventRequestDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
  ): Promise<CreateEventResponseDto> {
    const {users, ...event} = body;

    const result = await this.saveEventUseCase.execute({users, event, idempotencyKey});

    return unwrapResult(result);
  }

  @Get(UserRoutes.getEventInfo)
  @ApiResponse({status: HttpStatus.OK, type: GetEventInfoResponseDto})
  async getEventInfo(
    @Param() {eventId}: GetEventInfoParamsDto,
    @Query() query: GetEventInfoRequestV1Dto,
  ): Promise<GetEventInfoResponseDto> {
    const result = await this.getEventInfoUseCase.execute({eventId, pinCode: query.pinCode});

    return unwrapResult(result);
  }

  @Delete(UserRoutes.deleteEvent)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: DeleteEventResponseDto})
  async deleteEvent(
    @Param() {eventId}: DeleteEventParamsDto,
    @Body() body: DeleteEventRequestDto,
  ): Promise<DeleteEventResponseDto> {
    const result = await this.deleteEventUseCase.execute({eventId, pinCode: body.pinCode});

    return unwrapResult(result);
  }

  @Post(UserRoutes.addUsersToEvent)
  @HttpCode(HttpStatus.CREATED)
  @ApiResponse({status: HttpStatus.CREATED, type: [AddUsersToEventResponseDto]})
  async addUserToEvent(
    @Param() {eventId}: AddUsersToEventParamsDto,
    @Body() body: AddUsersToEventRequestDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
  ): Promise<AddUsersToEventResponseDto[]> {
    const result = await this.saveUsersToEventUseCase.execute({
      eventId,
      ...body,
      idempotencyKey,
    });

    return unwrapResult(result);
  }

  @Get(UserRoutes.getAllEventExpenses)
  @ApiResponse({status: HttpStatus.OK, type: [GetEventExpensesResponseDto]})
  async getAllEventExpenses(@Param() {eventId}: GetEventExpensesParamsDto): Promise<GetEventExpensesResponseDto[]> {
    const result = await this.getEventExpensesUseCase.execute({eventId});

    return unwrapResult(result);
  }

  @Post(UserRoutes.createExpense)
  @ApiResponse({status: HttpStatus.CREATED, type: CreateExpenseResponseDto})
  async createExpense(
    @Body() expense: CreateExpenseRequestV1Dto,
    @Param() {eventId}: CreateExpenseParamsDto,
    @Headers('idempotency-key') idempotencyKey: string | undefined,
  ): Promise<CreateExpenseResponseDto> {
    const result = await this.saveEventExpenseUseCase.execute({
      ...expense,
      eventId,
      isCustomRate: false,
      splitInformation: expense.splitInformation.map(({userId, amount}) => ({userId, amount, exchangedAmount: amount})),
      idempotencyKey,
    });

    return unwrapResult(result);
  }

  @Get(UserRoutes.getBanner)
  @Header('Cache-Control', 'public, max-age=21600')
  @Header('Vary', 'User-Agent')
  @ApiHeader({name: 'User-Agent', required: false, example: 'CommonEx/123 (Android/r)'})
  @ApiResponse({status: HttpStatus.OK, type: GetBannerResponseDto})
  @ApiResponse({status: HttpStatus.NO_CONTENT, description: 'No matching banner or unrecognized client version'})
  async getBanner(@Headers('user-agent') userAgent: string | undefined, @Res() response: BannerReply): Promise<void> {
    const client = parseClientVersion(userAgent);
    const banner = client == null ? null : await this.getBannerUseCase.execute(client);

    if (banner == null) {
      response.code(HttpStatus.NO_CONTENT).send();
    } else {
      response.code(HttpStatus.OK).send(banner);
    }
  }
}
