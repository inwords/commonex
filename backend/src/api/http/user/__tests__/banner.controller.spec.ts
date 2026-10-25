import {FastifyAdapter, NestFastifyApplication} from '@nestjs/platform-fastify';
import {Test} from '@nestjs/testing';

import {DeleteEventUseCase} from '#usecases/users/delete-event.usecase';
import {GetBannerUseCase} from '#usecases/users/get-banner.usecase';
import {GetEventExpensesUseCase} from '#usecases/users/get-event-expenses.usecase';
import {GetEventInfoUseCase} from '#usecases/users/get-event-info.usecase';
import {SaveEventExpenseUseCase} from '#usecases/users/save-event-expense.usecase';
import {SaveEventUseCase} from '#usecases/users/save-event.usecase';
import {SaveUsersToEventUseCase} from '#usecases/users/save-users-to-event.usecase';

import {UserController} from '../user.controller';

describe('UserController banner HTTP', () => {
  let app: NestFastifyApplication;
  const useCase: jest.Mocked<Pick<GetBannerUseCase, 'execute'>> = {execute: jest.fn()};

  beforeAll(async () => {
    const module = await Test.createTestingModule({
      controllers: [UserController],
      providers: [
        {provide: GetBannerUseCase, useValue: useCase},
        {provide: GetEventExpensesUseCase, useValue: {}},
        {provide: SaveEventExpenseUseCase, useValue: {}},
        {provide: SaveEventUseCase, useValue: {}},
        {provide: GetEventInfoUseCase, useValue: {}},
        {provide: SaveUsersToEventUseCase, useValue: {}},
        {provide: DeleteEventUseCase, useValue: {}},
      ],
    }).compile();
    app = module.createNestApplication<NestFastifyApplication>(new FastifyAdapter());
    await app.init();
    await app.getHttpAdapter().getInstance().ready();
  });

  afterAll(async () => {
    await app.close();
  });

  beforeEach(() => {
    useCase.execute.mockReset().mockResolvedValue(null);
  });

  it('returns the banner JSON and six-hour cache headers', async () => {
    const banner = {type: 'general_warning', text: 'Scheduled maintenance'} as const;
    useCase.execute.mockResolvedValue(banner);

    const response = await app.inject({
      method: 'GET',
      url: '/user/banner',
      headers: {'user-agent': 'CommonEx/123 (Android/r)'},
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toEqual(banner);
    expect(response.headers['cache-control']).toBe('public, max-age=21600');
    expect(response.headers.vary).toBe('User-Agent');
    expect(useCase.execute).toHaveBeenCalledWith({platform: 'Android', versionCode: 123});
  });

  it('returns a cacheable empty response when no banner matches', async () => {
    const response = await app.inject({
      method: 'GET',
      url: '/user/banner',
      headers: {'user-agent': 'CommonEx/123 (iOS/r)'},
    });

    expect(response.statusCode).toBe(204);
    expect(response.body).toBe('');
    expect(response.headers['cache-control']).toBe('public, max-age=21600');
    expect(response.headers.vary).toBe('User-Agent');
    expect(useCase.execute).toHaveBeenCalledWith({platform: 'iOS', versionCode: 123});
  });

  it.each([undefined, 'CommonEx/invalid (Android/r)', 'Mozilla/5.0'])(
    'returns a cacheable empty response without looking up an unrecognized client %s',
    async (userAgent) => {
      const response = await app.inject({
        method: 'GET',
        url: '/user/banner',
        headers: userAgent == null ? {} : {'user-agent': userAgent},
      });

      expect(response.statusCode).toBe(204);
      expect(response.body).toBe('');
      expect(response.headers['cache-control']).toBe('public, max-age=21600');
      expect(response.headers.vary).toBe('User-Agent');
      expect(useCase.execute).not.toHaveBeenCalled();
    },
  );
});
