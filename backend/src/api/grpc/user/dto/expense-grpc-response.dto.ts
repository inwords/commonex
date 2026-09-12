import {IExpense} from '#domain/entities/expense.entity';

import {toIsoString} from './iso-date';

export interface ExpenseGrpcResponseDto extends Omit<IExpense, 'createdAt' | 'updatedAt'> {
  createdAt: string;
  updatedAt: string;
}

export interface ExpensesGrpcResponseDto {
  expenses: ExpenseGrpcResponseDto[];
}

export const toExpenseGrpcResponse = (expense: IExpense): ExpenseGrpcResponseDto => ({
  ...expense,
  createdAt: toIsoString(expense.createdAt),
  updatedAt: toIsoString(expense.updatedAt),
});
