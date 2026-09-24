import assert from 'node:assert/strict';
import test from 'node:test';
import {getExpenseExchangeRate} from '../src/5-entities/expense/lib/exchange-rate.ts';

test('preserves the exchange rate for a reversal with negative split amounts', () => {
  const expense = {currencyId: 'USD', splitInformation: [{amount: -10, exchangedAmount: -9}]};

  assert.equal(getExpenseExchangeRate(expense, 'EUR'), 0.9);
});

test('uses a nonzero split when the first participant owes nothing', () => {
  const expense = {
    currencyId: 'USD',
    splitInformation: [{amount: 0, exchangedAmount: 0}, {amount: 10, exchangedAmount: 9}],
  };

  assert.equal(getExpenseExchangeRate(expense, 'EUR'), 0.9);
});

test('preserves a one-to-one rate between different currencies', () => {
  const expense = {currencyId: 'USD', splitInformation: [{amount: 10, exchangedAmount: 10}]};

  assert.equal(getExpenseExchangeRate(expense, 'EUR'), 1);
});

test('rounds the stored rate only when requested', () => {
  const expense = {currencyId: 'USD', splitInformation: [{amount: 100, exchangedAmount: 91.23}]};

  assert.equal(getExpenseExchangeRate(expense, 'EUR'), 0.9123);
  assert.equal(getExpenseExchangeRate(expense, 'EUR', {decimals: 2}), 0.91);
});

test('uses the default rate when no conversion can be derived', () => {
  const expense = {currencyId: 'USD', splitInformation: [{amount: 10, exchangedAmount: 9}]};

  assert.equal(getExpenseExchangeRate(expense, 'USD'), 1);
  assert.equal(getExpenseExchangeRate(expense, undefined), 1);
  assert.equal(getExpenseExchangeRate({...expense, splitInformation: []}, 'EUR'), 1);
  assert.equal(getExpenseExchangeRate({
    ...expense,
    splitInformation: [{amount: 0, exchangedAmount: 0}],
  }, 'EUR'), 1);
});
