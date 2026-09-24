import {Column, Entity, Index, PrimaryColumn} from 'typeorm';

import {type IExpense} from '#domain/entities/expense.entity';

@Entity('expense')
export class ExpenseEntity implements IExpense {
  @PrimaryColumn({type: 'varchar'})
  id!: IExpense['id'];

  @Column({type: 'varchar'})
  description!: IExpense['description'];

  @Column({type: 'varchar'})
  userWhoPaidId!: IExpense['userWhoPaidId'];

  @Column({type: 'varchar'})
  currencyId!: IExpense['currencyId'];

  @Column({type: 'varchar'})
  eventId!: IExpense['eventId'];

  @Column({type: 'varchar'})
  expenseType!: IExpense['expenseType'];

  @Column({type: 'jsonb'})
  splitInformation!: IExpense['splitInformation'];

  @Column({type: 'boolean'})
  isCustomRate!: IExpense['isCustomRate'];

  @Index('IDX_expense_reverts_expense_id')
  @Column({type: 'varchar', nullable: true})
  revertsExpenseId!: Exclude<IExpense['revertsExpenseId'], undefined>;

  @Index('IDX_expense_replaces_expense_id')
  @Column({type: 'varchar', nullable: true})
  replacesExpenseId!: Exclude<IExpense['replacesExpenseId'], undefined>;

  @Column({type: 'timestamptz'})
  createdAt!: IExpense['createdAt'];

  @Column({type: 'timestamptz'})
  updatedAt!: IExpense['updatedAt'];
}
