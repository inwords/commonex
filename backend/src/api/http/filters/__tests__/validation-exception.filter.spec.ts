import {ArgumentsHost, BadRequestException, HttpStatus} from '@nestjs/common';
import {AbstractHttpAdapter} from '@nestjs/core';

import {ValidationExceptionFilter} from '#api/http/filters/validation-exception.filter';

const run = (message: unknown): unknown[] => {
  const reply: jest.Mock<void, unknown[]> = jest.fn();
  const response = {};
  const host = {switchToHttp: () => ({getResponse: (): object => response})} as ArgumentsHost;

  new ValidationExceptionFilter({reply} as unknown as AbstractHttpAdapter).catch(
    new BadRequestException({message}),
    host,
  );

  return reply.mock.calls[0] ?? [];
};

describe('ValidationExceptionFilter', () => {
  it.each([
    ['passes a string message through', 'bad', 'bad'],
    ['joins an array of messages with a semicolon', ['a', 'b'], 'a; b'],
    ['serialises an object message', {field: 'x'}, '{"field":"x"}'],
    ['stringifies other values', 42, '42'],
  ])('%s', (_name, message, expected) => {
    expect(run(message)).toEqual([
      expect.anything(),
      {statusCode: HttpStatus.BAD_REQUEST, code: 'B4006', message: expected},
      HttpStatus.BAD_REQUEST,
    ]);
  });
});
