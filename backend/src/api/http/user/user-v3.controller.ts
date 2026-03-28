import {Controller, Get, Headers, HttpStatus, Res} from '@nestjs/common';
import {UserV3Routes} from './user.constants';
import {ApiResponse, ApiTags} from '@nestjs/swagger';

import {GetAllCurrenciesWithRatesResponseDto} from './dto/get-all-currencies.dto';
import {isError} from '#packages/result';
import {GetAllCurrenciesWithRatesUseCaseV3, GetCurrenciesV3VersionUseCase} from '#usecases/users/v3';
import {CurrenciesV3Version, isCurrenciesV3NotModified, setCurrenciesV3CacheHeaders} from '#usecases/users/v3/currencies-v3-cache';

interface RouteReply {
  code: (statusCode: number) => RouteReply;
  header: (name: string, value: string) => void;
  send: (payload?: GetAllCurrenciesWithRatesResponseDto) => void;
}

interface GetAllCurrenciesRouteData {
  response: GetAllCurrenciesWithRatesResponseDto;
  version: CurrenciesV3Version;
}

const normalizeIfNoneMatchHeader = (ifNoneMatchHeader: string | string[] | undefined): string | undefined => {
  return Array.isArray(ifNoneMatchHeader) ? ifNoneMatchHeader.join(',') : ifNoneMatchHeader;
};

@Controller(UserV3Routes.root)
@ApiTags('User V3')
export class UserV3Controller {
  constructor(
    private readonly getCurrenciesV3VersionUseCase: GetCurrenciesV3VersionUseCase,
    private readonly getAllCurrenciesWithRatesUseCase: GetAllCurrenciesWithRatesUseCaseV3,
  ) {}

  @Get(UserV3Routes.getAllCurrencies)
  @ApiResponse({status: HttpStatus.OK, type: GetAllCurrenciesWithRatesResponseDto})
  @ApiResponse({status: HttpStatus.NOT_MODIFIED, description: 'Currencies not modified'})
  async getAllCurrencies(@Headers('if-none-match') ifNoneMatchHeader: string | string[] | undefined, @Res() response: RouteReply): Promise<void> {
    const ifNoneMatch = normalizeIfNoneMatchHeader(ifNoneMatchHeader);
    let payload: GetAllCurrenciesRouteData | null = null;
    let version: CurrenciesV3Version;

    if (ifNoneMatch == null) {
      payload = await this.getAllCurrenciesOrThrow();
      version = payload.version;
    } else {
      version = await this.getVersionOrThrow();

      if (!isCurrenciesV3NotModified(ifNoneMatch, version)) {
        payload = await this.getAllCurrenciesOrThrow();
        version = payload.version;
      }
    }

    setCurrenciesV3CacheHeaders(response, version);
    if (payload == null) {
      response.code(HttpStatus.NOT_MODIFIED).send();
      return;
    }

    response.code(HttpStatus.OK).send(payload.response);
  }

  private async getVersionOrThrow(): Promise<CurrenciesV3Version> {
    const result = await this.getCurrenciesV3VersionUseCase.execute();

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }

  private async getAllCurrenciesOrThrow(): Promise<GetAllCurrenciesRouteData> {
    const result = await this.getAllCurrenciesWithRatesUseCase.execute();

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }
}
