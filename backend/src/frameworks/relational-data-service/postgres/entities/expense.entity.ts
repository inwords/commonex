import {Column, Entity, PrimaryColumn} from 'typeorm';
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

  @Column({type: 'timestamptz'})
  createdAt!: IExpense['createdAt'];

  @Column({type: 'timestamptz'})
  updatedAt!: IExpense['updatedAt'];
}
