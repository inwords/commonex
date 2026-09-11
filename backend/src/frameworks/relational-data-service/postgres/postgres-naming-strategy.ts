import {type NamingStrategyInterface, type Table} from 'typeorm';
import {SnakeNamingStrategy} from 'typeorm-naming-strategies';

export class PostgresNamingStrategy extends SnakeNamingStrategy implements NamingStrategyInterface {
  public override primaryKeyName(tableOrName: string | Table, columnNames: string[]): string {
    return `pk__${this.getTableName(tableOrName)}__${this.getJoinedColumns(columnNames)}`;
  }

  public override uniqueConstraintName(tableOrName: string | Table, columnNames: string[]): string {
    return `uq__${this.getTableName(tableOrName)}__${this.getJoinedColumns(columnNames)}`;
  }

  public override indexName(tableOrName: string | Table, columnNames: string[]): string {
    return `idx__${this.getTableName(tableOrName)}__${this.getJoinedColumns(columnNames)}`;
  }

  public override foreignKeyName(tableOrName: string | Table, columnNames: string[]): string {
    return `fk__${this.getTableName(tableOrName)}__${this.getJoinedColumns(columnNames)}`;
  }

  private getJoinedColumns(columnNames: string[]): string {
    return columnNames.join('__');
  }
}
