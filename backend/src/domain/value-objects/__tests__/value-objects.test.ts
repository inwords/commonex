import {CurrencyCode} from '#domain/entities/currency.entity';
import {ExpenseType} from '#domain/entities/expense.entity';
import {CurrencyRateValueObject} from '#domain/value-objects/currency-rate.value-object';
import {CurrencyValueObject} from '#domain/value-objects/currency.value-object';
import {EventShareTokenValueObject} from '#domain/value-objects/event-share-token.value-object';
import {EventValueObject} from '#domain/value-objects/event.value-object';
import {ExpenseValueObject} from '#domain/value-objects/expense.value-object';
import {IdempotencyKeyValueObject} from '#domain/value-objects/idempotency-key.value-object';
import {UserInfoValueObject} from '#domain/value-objects/user-info.value-object';

const ULID = /^[0-9A-HJKMNP-TV-Z]{26}$/;
const now = new Date('2026-01-01T00:00:00.000Z');

describe('value objects', () => {
  beforeEach(() => {
    jest.useFakeTimers({now});
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('fills id and timestamps on EventValueObject and defaults deletedAt to null', () => {
    const {value} = new EventValueObject({name: 'Trip', currencyId: 'c-usd', pinCode: '1234'});

    expect(value).toEqual({
      id: expect.stringMatching(ULID),
      name: 'Trip',
      currencyId: 'c-usd',
      pinCode: '1234',
      createdAt: now,
      updatedAt: now,
      deletedAt: null,
    });
  });

  it('keeps provided values over defaults', () => {
    const createdAt = new Date('2020-01-01T00:00:00Z');

    const {value} = new EventValueObject({id: 'fixed', name: 'Trip', currencyId: 'c', pinCode: '1', createdAt});

    expect(value).toMatchObject({id: 'fixed', createdAt});
  });

  it('treats an explicit undefined as absent so class-validator output does not erase defaults', () => {
    // exactOptionalPropertyTypes forbids writing `id: undefined` directly; class-validator produces it at runtime.
    const explicitUndefinedId = {id: undefined} as unknown as {id?: string};

    const {value} = new ExpenseValueObject({
      ...explicitUndefinedId,
      description: 'Lunch',
      userWhoPaidId: 'u1',
      currencyId: 'c',
      eventId: 'e',
      expenseType: ExpenseType.Expense,
      splitInformation: [],
      isCustomRate: false,
    });

    expect(value.id).toMatch(ULID);
  });

  it('fills id and timestamps on UserInfoValueObject and CurrencyValueObject', () => {
    expect(new UserInfoValueObject({name: 'Alice', eventId: 'e'}).value).toMatchObject({
      id: expect.stringMatching(ULID),
      createdAt: now,
      updatedAt: now,
    });
    expect(new CurrencyValueObject({code: CurrencyCode.USD}).value).toMatchObject({
      id: expect.stringMatching(ULID),
      createdAt: now,
      updatedAt: now,
    });
  });

  it('fills timestamps on CurrencyRateValueObject', () => {
    expect(new CurrencyRateValueObject({date: '2026-01-01', rate: {USD: 1}}).value).toEqual({
      date: '2026-01-01',
      rate: {USD: 1},
      createdAt: now,
      updatedAt: now,
    });
  });

  it('generates a 64-hex token that expires in 14 days', () => {
    const {value} = new EventShareTokenValueObject({eventId: 'e'});

    expect(value.token).toMatch(/^[0-9a-f]{64}$/);
    expect(value.expiresAt).toEqual(new Date('2026-01-15T00:00:00.000Z'));
    expect(value.createdAt).toEqual(now);
  });

  it('expires an idempotency key after 24 hours', () => {
    const {value} = new IdempotencyKeyValueObject({
      key: 'k',
      url: '/u',
      requestHash: 'h',
      response: {},
      statusCode: 200,
    });

    expect(value.expiresAt).toEqual(new Date('2026-01-02T00:00:00.000Z'));
    expect(value.createdAt).toEqual(now);
  });
});
