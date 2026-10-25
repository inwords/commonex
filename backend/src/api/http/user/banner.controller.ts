import {Controller, Get, Header, Headers, HttpStatus, Res} from '@nestjs/common';
import {ApiHeader, ApiResponse, ApiTags} from '@nestjs/swagger';

import {GetBannerUseCase} from '#usecases/users/get-banner.usecase';

import {GetBannerResponseDto} from './dto/get-banner.dto';
import {parseClientVersion} from './parse-client-version';
import {UserRoutes} from './user.constants';

interface BannerReply {
  code: (status: number) => BannerReply;
  send: (payload?: GetBannerResponseDto) => void;
}

@Controller(UserRoutes.root)
@ApiTags('User')
export class BannerController {
  constructor(private readonly getBannerUseCase: GetBannerUseCase) {}

  @Get(UserRoutes.getBanner)
  @Header('Cache-Control', 'public, max-age=21600')
  @Header('Vary', 'User-Agent')
  @ApiHeader({name: 'User-Agent', required: false, example: 'CommonEx/123 (Android/r)'})
  @ApiResponse({status: HttpStatus.OK, type: GetBannerResponseDto})
  @ApiResponse({status: HttpStatus.NO_CONTENT, description: 'No matching banner or unrecognized client version'})
  async getBanner(@Headers('user-agent') userAgent: string | undefined, @Res() response: BannerReply): Promise<void> {
    const client = parseClientVersion(userAgent);
    const banner = client == null ? null : await this.getBannerUseCase.execute(client);

    if (banner == null) {
      response.code(HttpStatus.NO_CONTENT).send();
    } else {
      response.code(HttpStatus.OK).send(banner);
    }
  }
}
