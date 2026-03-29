import {ApiProperty} from '@nestjs/swagger';
import {CurrencyCode} from '#domain/entities/currency.entity';

export class CurrencyV3ResponseDto {
  @ApiProperty()
  id!: string;

  @ApiProperty({enum: CurrencyCode})
  code!: CurrencyCode;

  @ApiProperty()
  updatedAt!: Date;
}

export class GetAllCurrenciesWithRatesResponseDto {
  @ApiProperty({type: [CurrencyV3ResponseDto]})
  currencies!: CurrencyV3ResponseDto[];

  @ApiProperty({
    description: 'Exchange rates relative to USD',
    type: 'object',
    additionalProperties: {type: 'number'},
    example: {USD: 1, EUR: 0.92, RUB: 95.5},
  })
  exchangeRate!: Record<string, number>;
}
