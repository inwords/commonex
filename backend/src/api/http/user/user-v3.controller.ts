import {Controller, Get, HttpCode, HttpStatus} from '@nestjs/common';
import {UserV3Routes} from './user.constants';
import {ApiResponse, ApiTags} from '@nestjs/swagger';

import {GetAllCurrenciesWithRatesResponseDto} from './dto/get-all-currencies.dto';
import {isError} from '#packages/result';
import {GetAllCurrenciesWithRatesUseCaseV3} from '#usecases/users/v3';

@Controller(UserV3Routes.root)
@ApiTags('User V3')
export class UserV3Controller {
  constructor(private readonly getAllCurrenciesWithRatesUseCase: GetAllCurrenciesWithRatesUseCaseV3) {}

  @Get(UserV3Routes.getAllCurrencies)
  @HttpCode(HttpStatus.OK)
  @ApiResponse({status: HttpStatus.OK, type: GetAllCurrenciesWithRatesResponseDto})
  async getAllCurrencies(): Promise<GetAllCurrenciesWithRatesResponseDto> {
    const result = await this.getAllCurrenciesWithRatesUseCase.execute();

    if (isError(result)) {
      throw result.error;
    }

    return result.value;
  }
}
