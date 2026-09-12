import {error, success} from '#packages/result';

import {IEvent} from '#domain/entities/event.entity';
import {EventDeletedError, EventNotFoundError, InvalidPinCodeError} from '#domain/errors/errors';

import {EventService} from '#frameworks/event-service/event-service';

const event: IEvent = {
  id: 'event-1',
  name: 'Trip',
  currencyId: 'c-usd',
  pinCode: '1234',
  createdAt: new Date('2023-01-01T00:00:00Z'),
  updatedAt: new Date('2023-01-01T00:00:00Z'),
  deletedAt: null,
};

describe('EventService', () => {
  const service = new EventService();

  it.each([
    ['returns EventNotFoundError for null', null, '1234', error(new EventNotFoundError())],
    ['returns EventNotFoundError for undefined', undefined, '1234', error(new EventNotFoundError())],
    [
      'returns EventDeletedError for a deleted event',
      {...event, deletedAt: new Date()},
      '1234',
      error(new EventDeletedError()),
    ],
    ['returns InvalidPinCodeError for a wrong pin', event, '0000', error(new InvalidPinCodeError())],
    ['returns success for a live event with the right pin', event, '1234', success(true)],
  ])('%s', (_name, input, pinCode, expected) => {
    expect(service.isValidEvent(input, pinCode)).toEqual(expected);
  });

  it('checks deletion before the pin code', () => {
    expect(service.isValidEvent({...event, deletedAt: new Date()}, '0000')).toEqual(error(new EventDeletedError()));
  });
});
