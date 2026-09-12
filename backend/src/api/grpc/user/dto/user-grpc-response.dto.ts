import {IExpense} from '#domain/entities/expense.entity';

import {DeleteEventResponseDto} from '#api/http/user/dto/delete-event.dto';

export interface ExpenseGrpcResponseDto extends Omit<IExpense, 'createdAt' | 'updatedAt'> {
  createdAt: string;
  updatedAt: string;
}

export interface ExpensesGrpcResponseDto {
  expenses: ExpenseGrpcResponseDto[];
}

export interface DeleteEventGrpcResponseDto {
  id: string;
  deletedAt: string;
}

// An idempotency-key replay returns the cached response round-tripped through the `idempotency_keys.response` jsonb
// column, so `createdAt`/`updatedAt` arrive as ISO strings rather than `Date` instances despite the `IExpense` type.
const toIsoString = (value: Date | string): string => (value instanceof Date ? value : new Date(value)).toISOString();

export const toExpenseGrpcResponse = (expense: IExpense): ExpenseGrpcResponseDto => ({
  ...expense,
  createdAt: toIsoString(expense.createdAt),
  updatedAt: toIsoString(expense.updatedAt),
});

export const toDeleteEventGrpcResponse = (deletedEvent: DeleteEventResponseDto): DeleteEventGrpcResponseDto => ({
  id: deletedEvent.id,
  deletedAt: toIsoString(deletedEvent.deletedAt),
});
