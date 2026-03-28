import {UserV3Controller} from '../user-v3.controller';
import {GetAllCurrenciesWithRatesResponseDto} from '../dto/get-all-currencies.dto';
import {GetAllCurrenciesWithRatesUseCaseV3, GetCurrenciesV3VersionUseCase} from '#usecases/users/v3';
import {success} from '#packages/result';
import {CurrencyCode} from '#domain/entities/currency.entity';
import {buildCurrenciesV3WeakEtag} from '#usecases/users/v3/currencies-v3-cache';

type RouteReply = {
  code: jest.MockedFunction<(statusCode: number) => RouteReply>;
  header: jest.MockedFunction<(name: string, value: string) => void>;
  send: jest.MockedFunction<(payload?: GetAllCurrenciesWithRatesResponseDto) => void>;
};

const createReplyMock = (): RouteReply => {
  const reply = {} as RouteReply;

  reply.code = jest.fn().mockImplementation(() => reply);
  reply.header = jest.fn();
  reply.send = jest.fn();

  return reply;
};

describe('UserV3Controller', () => {
  const version = {
    rateUpdatedAt: new Date('2026-01-06T12:00:00Z'),
    currenciesUpdatedAt: [new Date('2023-01-01T00:00:00Z'), new Date('2023-01-02T00:00:00Z')],
  };
  const responseBody = {
    currencies: [
      {
        id: 'currency-eur',
        code: CurrencyCode.EUR,
        updatedAt: new Date('2023-01-01T00:00:00Z'),
      },
      {
        id: 'currency-usd',
        code: CurrencyCode.USD,
        updatedAt: new Date('2023-01-02T00:00:00Z'),
      },
    ],
    exchangeRate: {
      EUR: 0.92,
      USD: 1,
    },
  };
  const result = success({
    response: responseBody,
    version,
  });

  let controller: UserV3Controller;
  let versionUseCase: jest.Mocked<Pick<GetCurrenciesV3VersionUseCase, 'execute'>>;
  let useCase: jest.Mocked<Pick<GetAllCurrenciesWithRatesUseCaseV3, 'execute'>>;

  beforeEach(() => {
    versionUseCase = {
      execute: jest.fn().mockResolvedValue(success(version)),
    };
    useCase = {
      execute: jest.fn().mockResolvedValue(result),
    };
    controller = new UserV3Controller(versionUseCase as unknown as GetCurrenciesV3VersionUseCase, useCase as unknown as GetAllCurrenciesWithRatesUseCaseV3);
  });

  it('returns currencies payload with weak etag and cache control', async () => {
    const reply = createReplyMock();
    const etag = buildCurrenciesV3WeakEtag(version);

    await controller.getAllCurrencies(undefined, reply);

    expect(versionUseCase.execute).not.toHaveBeenCalled();
    expect(useCase.execute).toHaveBeenCalledTimes(1);
    expect(reply.header).toHaveBeenNthCalledWith(1, 'Cache-Control', 'private, no-cache');
    expect(reply.header).toHaveBeenNthCalledWith(2, 'ETag', etag);
    expect(reply.code).toHaveBeenCalledWith(200);
    expect(reply.send).toHaveBeenCalledWith(responseBody);
  });

  it('returns 304 when If-None-Match matches the current representation without loading the full payload', async () => {
    const reply = createReplyMock();
    const weakEtag = buildCurrenciesV3WeakEtag(version);
    const strongEquivalent = weakEtag.replace(/^W\//, '');

    await controller.getAllCurrencies(strongEquivalent, reply);

    expect(versionUseCase.execute).toHaveBeenCalledTimes(1);
    expect(useCase.execute).not.toHaveBeenCalled();
    expect(reply.header).toHaveBeenNthCalledWith(1, 'Cache-Control', 'private, no-cache');
    expect(reply.header).toHaveBeenNthCalledWith(2, 'ETag', weakEtag);
    expect(reply.code).toHaveBeenCalledWith(304);
    expect(reply.send).toHaveBeenCalledWith();
  });

  it('loads the full payload when If-None-Match is stale', async () => {
    const reply = createReplyMock();

    await controller.getAllCurrencies('W/"currencies-v3-stale"', reply);

    expect(versionUseCase.execute).toHaveBeenCalledTimes(1);
    expect(useCase.execute).toHaveBeenCalledTimes(1);
    expect(reply.header).toHaveBeenNthCalledWith(1, 'Cache-Control', 'private, no-cache');
    expect(reply.header).toHaveBeenNthCalledWith(2, 'ETag', buildCurrenciesV3WeakEtag(version));
    expect(reply.code).toHaveBeenCalledWith(200);
    expect(reply.send).toHaveBeenCalledWith(responseBody);
  });

  it('normalizes repeated If-None-Match header values before validation', async () => {
    const reply = createReplyMock();
    const weakEtag = buildCurrenciesV3WeakEtag(version);
    const strongEquivalent = weakEtag.replace(/^W\//, '');

    await controller.getAllCurrencies(['"other-etag"', strongEquivalent], reply);

    expect(versionUseCase.execute).toHaveBeenCalledTimes(1);
    expect(useCase.execute).not.toHaveBeenCalled();
    expect(reply.header).toHaveBeenNthCalledWith(1, 'Cache-Control', 'private, no-cache');
    expect(reply.header).toHaveBeenNthCalledWith(2, 'ETag', weakEtag);
    expect(reply.code).toHaveBeenCalledWith(304);
    expect(reply.send).toHaveBeenCalledWith();
  });
});
