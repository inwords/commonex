import {Controller, Get, HttpCode, HttpStatus} from '@nestjs/common';
import {UserV3Routes} from './user.constants';
import {ApiResponse, ApiTags} from '@nestjs/swagger';

import {GetAllCurrenciesWithRatesResponseDto} from './dto/get-all-currencies.dto';
import {GetAllCurrenciesWithRatesUseCase} from '#usecases/users/get-all-currencies-with-rates.usecase';
import {isError} from '#packages/result';

@Controller(UserV3Routes.root)
@ApiTags('User V3')
export class UserV3Controller {
  constructor(private readonly getAllCurrenciesWithRatesUseCase: GetAllCurrenciesWithRatesUseCase) {}

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
