import {CurrencyEntity} from './currency.entity';
import {EventEntity} from './event.entity';
import {CurrencyRateEntity} from './currency-rate.entity';
import {ExpenseEntity} from './expense.entity';
import {UserInfoEntity} from './user-info.entity';
import {EventShareTokenEntity} from './event-share-token.entity';
import {IdempotencyKeyEntity} from './idempotency-key.entity';

export const allEntities = [
  CurrencyEntity,
  EventEntity,
  CurrencyRateEntity,
  ExpenseEntity,
  UserInfoEntity,
  EventShareTokenEntity,
  IdempotencyKeyEntity,
];
