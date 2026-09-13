import {DefaultNamingStrategy, type NamingStrategyInterface, type Table} from 'typeorm';
import {snakeCase} from 'typeorm/util/StringUtils';

export class PostgresNamingStrategy extends DefaultNamingStrategy implements NamingStrategyInterface {
  public override tableName(className: string, customName: string | undefined): string {
    if (customName) {
      return customName;
    }

    return snakeCase(className);
  }

  public override columnName(propertyName: string, customName: string | undefined, embeddedPrefixes: string[]): string {
    const prefix = snakeCase(embeddedPrefixes.concat('').join('_'));
    if (customName) {
      return prefix + customName;
    }

    return prefix + snakeCase(propertyName);
  }

  public override relationName(propertyName: string): string {
    return snakeCase(propertyName);
  }

  public override joinColumnName(relationName: string, referencedColumnName: string): string {
    return snakeCase(`${relationName}_${referencedColumnName}`);
  }

  public override joinTableName(firstTableName: string, secondTableName: string, firstPropertyName: string): string {
    return snakeCase(`${firstTableName}_${firstPropertyName.replace(/\./g, '_')}_${secondTableName}`);
  }

  public override joinTableColumnName(tableName: string, propertyName: string, columnName?: string): string {
    if (columnName) {
      return snakeCase(`${tableName}_${columnName}`);
    }

    return snakeCase(`${tableName}_${propertyName}`);
  }

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
