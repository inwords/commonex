import {IBanner} from '#domain/entities/banner.entity';

import {GetBannerUseCase} from '#usecases/users/get-banner.usecase';

import {RelationalDataService} from '#frameworks/relational-data-service/postgres/relational-data-service';

import {createTestRelationalDataService, truncateAllTables} from '#test-support/db';
import {TestCase, prepareInitRelationalState, validateFinalRelationalState} from '#test-support/relational-state';

describe('GetBannerUseCase', () => {
  let dataService: RelationalDataService;
  let useCase: GetBannerUseCase;

  beforeAll(async () => {
    dataService = createTestRelationalDataService();
    await dataService.initialize();
    useCase = new GetBannerUseCase(dataService);
  });

  afterAll(async () => {
    await dataService.destroy();
  });

  beforeEach(async () => {
    await truncateAllTables(dataService.dataSource);
  });

  const warning: IBanner = {
    id: 1,
    type: 'general_warning',
    text: 'Scheduled maintenance',
    platform: 'Android',
    minVersionCode: 100,
    maxVersionCode: 200,
    enabled: true,
  };
  const input = {platform: 'Android', versionCode: 150} as const;
  const output = {type: warning.type, text: warning.text};
  const testCases: TestCase<GetBannerUseCase>[] = [
    {name: 'returns no banner when none are configured', initRelationalState: {}, input, output: null},
    {name: 'returns the matching warning', initRelationalState: {banners: [warning]}, input, output},
    {
      name: 'includes the minimum version',
      initRelationalState: {banners: [warning]},
      input: {...input, versionCode: 100},
      output,
    },
    {
      name: 'includes the maximum version',
      initRelationalState: {banners: [warning]},
      input: {...input, versionCode: 200},
      output,
    },
    {
      name: 'excludes versions below the minimum',
      initRelationalState: {banners: [warning]},
      input: {...input, versionCode: 99},
      output: null,
    },
    {
      name: 'excludes versions above the maximum',
      initRelationalState: {banners: [warning]},
      input: {...input, versionCode: 201},
      output: null,
    },
    {
      name: 'excludes other platforms',
      initRelationalState: {banners: [warning]},
      input: {...input, platform: 'iOS'},
      output: null,
    },
    {
      name: 'excludes disabled banners',
      initRelationalState: {banners: [{...warning, enabled: false}]},
      input,
      output: null,
    },
    {
      name: 'matches an unbounded version range',
      initRelationalState: {banners: [{...warning, minVersionCode: 0, maxVersionCode: null}]},
      input: {...input, versionCode: 2147483647},
      output,
    },
    {
      name: 'prioritizes updates over warnings',
      initRelationalState: {
        banners: [
          {...warning, type: 'update_required', text: 'Update now'},
          {...warning, id: 2},
        ],
      },
      input,
      output: {type: 'update_required', text: 'Update now'},
    },
    {
      name: 'chooses the highest ID among banners of the same type',
      initRelationalState: {banners: [warning, {...warning, id: 2, text: 'New warning'}]},
      input,
      output: {type: 'general_warning', text: 'New warning'},
    },
  ];

  it.each(testCases)('$name', async ({initRelationalState, input, output}) => {
    await prepareInitRelationalState({rDataService: dataService, initState: initRelationalState});
    expect(await useCase.execute(input)).toEqual(output);
    await validateFinalRelationalState({rDataService: dataService, expectedState: initRelationalState});
  });
});
