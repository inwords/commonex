import {ApiProperty} from '@nestjs/swagger';

import {IBanner} from '#domain/entities/banner.entity';

export class GetBannerResponseDto {
  @ApiProperty({enum: ['general_warning', 'update_required']})
  type!: IBanner['type'];

  @ApiProperty()
  text!: string;
}
