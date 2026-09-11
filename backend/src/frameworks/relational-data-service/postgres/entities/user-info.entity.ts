import {Column, Entity, PrimaryColumn} from 'typeorm';

import {type IUserInfo} from '#domain/entities/user-info.entity';

@Entity('user_info')
export class UserInfoEntity implements IUserInfo {
  @PrimaryColumn({type: 'varchar'})
  id!: IUserInfo['id'];

  @Column({type: 'varchar'})
  name!: IUserInfo['name'];

  @Column({type: 'varchar'})
  eventId!: IUserInfo['eventId'];

  @Column({type: 'timestamptz'})
  createdAt!: IUserInfo['createdAt'];

  @Column({type: 'timestamptz'})
  updatedAt!: IUserInfo['updatedAt'];
}
