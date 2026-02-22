import {ApiProperty} from '@nestjs/swagger';
import {CurrencyCode} from '#domain/entities/currency.entity';

export class CurrencyResponseDto {
  @ApiProperty()
  id!: string;

  @ApiProperty({enum: CurrencyCode})
  code!: CurrencyCode;

  @ApiProperty()
  createdAt!: Date;

  @ApiProperty()
  updatedAt!: Date;
}

export class GetAllCurrenciesResponseDto {
  @ApiProperty({type: [CurrencyResponseDto]})
  currencies!: CurrencyResponseDto[];
}

export class GetAllCurrenciesWithRatesResponseDto {
  @ApiProperty({type: [CurrencyResponseDto]})
  currencies!: CurrencyResponseDto[];

  @ApiProperty({
    description: 'Exchange rates relative to USD',
    type: 'object',
    additionalProperties: {type: 'number'},
    example: {USD: 1, EUR: 0.92, RUB: 95.5},
  })
  exchangeRate!: Record<string, number>;
}
