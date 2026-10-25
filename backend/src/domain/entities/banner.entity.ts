import {IClientVersion} from './client-version';

export interface IBanner {
  id: number;
  type: 'general_warning' | 'update_required';
  text: string;
  platform: IClientVersion['platform'];
  minVersionCode: number;
  maxVersionCode: number | null;
  enabled: boolean;
}
