import {Check, Column, Entity, PrimaryGeneratedColumn} from 'typeorm';

import {IBanner} from '#domain/entities/banner.entity';

@Entity({name: 'banner'})
@Check('ck__banner__type', "type IN ('general_warning', 'update_required')")
@Check('ck__banner__platform', "platform IN ('Android', 'iOS')")
@Check('ck__banner__text', "text ~ '[^[:space:]]'")
@Check(
  'ck__banner__version_range',
  'min_version_code >= 0 AND (max_version_code IS NULL OR max_version_code >= min_version_code)',
)
export class BannerEntity implements IBanner {
  @PrimaryGeneratedColumn({type: 'integer'})
  id!: IBanner['id'];

  @Column({type: 'varchar'})
  type!: IBanner['type'];

  @Column({type: 'text'})
  text!: IBanner['text'];

  @Column({type: 'varchar'})
  platform!: IBanner['platform'];

  @Column({type: 'integer', default: 0})
  minVersionCode!: IBanner['minVersionCode'];

  @Column({type: 'integer', nullable: true})
  maxVersionCode!: IBanner['maxVersionCode'];

  @Column({type: 'boolean', default: false})
  enabled!: IBanner['enabled'];
}
