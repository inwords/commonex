import {ApiError} from './errors';
import {errorStore} from '@/6-shared/stores/error-store';

export class HttpClient {
  async request(input: RequestInfo | URL, init?: RequestInit & {idempotencyKey?: string}) {
    const baseUrl = window.location.origin.includes('localhost') ? 'http://localhost:3001' : '/api';

    const {idempotencyKey, ...restInit} = init ?? {};

    const resp = await fetch(`${baseUrl}${input}`, {
      ...restInit,
      headers: {
        ...restInit.headers,
        'Content-Type': 'application/json',
        ...(idempotencyKey ? {'idempotency-key': idempotencyKey} : {}),
      },
    });

    const data = await resp.json();

    if (!resp.ok) {
      const apiError: ApiError = {
        statusCode: data.statusCode || resp.status,
        code: data.code || 'UNKNOWN',
        message: data.message || resp.statusText,
      };

      errorStore.setError(apiError);
      throw apiError;
    }

    return data;
  }
}

export const httpClient = new HttpClient();
