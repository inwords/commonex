import {IExpense} from '#domain/entities/expense.entity';

export interface ExpenseGrpcResponseDto extends Omit<IExpense, 'createdAt' | 'updatedAt'> {
  createdAt: string;
  updatedAt: string;
}

export interface ExpensesGrpcResponseDto {
  expenses: ExpenseGrpcResponseDto[];
}

export const toExpenseGrpcResponse = (expense: IExpense): ExpenseGrpcResponseDto => ({
  ...expense,
  createdAt: expense.createdAt.toISOString(),
  updatedAt: expense.updatedAt.toISOString(),
});
