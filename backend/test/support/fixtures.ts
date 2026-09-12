import {NestFastifyApplication} from '@nestjs/platform-fastify';

import {getCurrentDateWithoutTimeUTC} from '#packages/date-utils';

import {CurrencyCode} from '#domain/entities/currency.entity';
import {CurrencyRateValueObject} from '#domain/value-objects/currency-rate.value-object';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {CreateEventResponseDto} from '#api/http/user/dto/create-event.dto';

export const findCurrencyIdByCode = async (
  rDataService: RelationalDataService,
  code: CurrencyCode,
): Promise<string> => {
  const [currencies] = await rDataService.currency.findAll({codes: [code]});
  const currency = currencies[0];

  if (currency === undefined) {
    throw new Error(`Currency ${code} is not seeded`);
  }

  return currency.id;
};

export const insertTodayRate = async (
  rDataService: RelationalDataService,
  rate: Record<string, number>,
): Promise<void> => {
  await rDataService.currencyRate.insert(
    new CurrencyRateValueObject({date: getCurrentDateWithoutTimeUTC(), rate}).value,
  );
};

export const createEvent = async (
  app: NestFastifyApplication,
  input: {name?: string; currencyId: string; pinCode?: string; users?: {name: string}[]},
): Promise<CreateEventResponseDto> => {
  const response = await app.inject({
    method: 'POST',
    url: '/user/event',
    payload: {name: 'Trip', pinCode: '1234', users: [{name: 'Alice'}, {name: 'Bob'}], ...input},
  });

  if (response.statusCode !== 201) {
    throw new Error(`createEvent failed: ${response.statusCode} ${response.body}`);
  }

  return response.json<CreateEventResponseDto>();
};
