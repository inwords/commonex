import {PostgresNamingStrategy} from '../postgres-naming-strategy';

describe('PostgresNamingStrategy', () => {
  const strategy = new PostgresNamingStrategy();

  it('preserves explicit table names and snake-cases generated table names', () => {
    expect(strategy.tableName('EventShareToken', undefined)).toBe('event_share_token');
    expect(strategy.tableName('EventShareToken', '')).toBe('event_share_token');
    expect(strategy.tableName('IgnoredClassName', 'existing_table')).toBe('existing_table');
  });

  it('snake-cases generated column and relation names', () => {
    expect(strategy.columnName('createdAt', undefined, [])).toBe('created_at');
    expect(strategy.columnName('createdAt', '', [])).toBe('created_at');
    expect(strategy.columnName('postalCode', undefined, ['billingAddress'])).toBe('billing_address_postal_code');
    expect(strategy.columnName('ignoredProperty', 'existing_column', [])).toBe('existing_column');
    expect(strategy.relationName('eventOwner')).toBe('event_owner');
  });

  it('preserves snake-case join naming', () => {
    expect(strategy.joinColumnName('eventOwner', 'userId')).toBe('event_owner_user_id');
    expect(strategy.joinTableName('userInfo', 'sharedEvent', 'events.items')).toBe(
      'user_info_events_items_shared_event',
    );
    expect(strategy.joinTableColumnName('userInfo', 'eventId')).toBe('user_info_event_id');
    expect(strategy.joinTableColumnName('userInfo', 'eventId', '')).toBe('user_info_event_id');
    expect(strategy.joinTableColumnName('userInfo', 'ignoredProperty', 'eventId')).toBe('user_info_event_id');
  });

  it('preserves project-specific constraint and index names', () => {
    expect(strategy.primaryKeyName('event_share_token', ['token'])).toBe('pk__event_share_token__token');
    expect(strategy.uniqueConstraintName('event_share_token', ['event_id', 'token'])).toBe(
      'uq__event_share_token__event_id__token',
    );
    expect(strategy.indexName('event_share_token', ['event_id'])).toBe('idx__event_share_token__event_id');
    expect(strategy.foreignKeyName('event_share_token', ['event_id'])).toBe('fk__event_share_token__event_id');
  });
});
