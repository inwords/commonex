import {Injectable} from '@nestjs/common';

import {UseCase} from '#packages/use-case';

import {RelationalDataServiceAbstract} from '#domain/abstracts/relational-data-service/relational-data-service';
import {IBanner} from '#domain/entities/banner.entity';
import {IClientVersion} from '#domain/entities/client-version';

type Output = Pick<IBanner, 'type' | 'text'> | null;

@Injectable()
export class GetBannerUseCase implements UseCase<IClientVersion, Output> {
  constructor(private readonly rDataService: RelationalDataServiceAbstract) {}

  async execute(client: IClientVersion): Promise<Output> {
    const [banners] = await this.rDataService.banner.findAll({client, enabled: true});
    const banner = banners.find(({type}) => type === 'update_required') ?? banners[0];

    return banner == null ? null : {type: banner.type, text: banner.text};
  }
}
