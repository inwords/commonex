import {CurrencyRateEntity} from './currency-rate.entity';
import {CurrencyEntity} from './currency.entity';
import {EventShareTokenEntity} from './event-share-token.entity';
import {EventEntity} from './event.entity';
import {ExpenseEntity} from './expense.entity';
import {IdempotencyKeyEntity} from './idempotency-key.entity';
import {UserInfoEntity} from './user-info.entity';

export const allEntities = [
  CurrencyEntity,
  EventEntity,
  CurrencyRateEntity,
  ExpenseEntity,
  UserInfoEntity,
  EventShareTokenEntity,
  IdempotencyKeyEntity,
];
