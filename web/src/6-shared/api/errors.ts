import {languageStore} from '@/6-shared/i18n/languageStore';

export interface ApiError {
  statusCode: number;
  code: string;
  message: string;
}

export enum ApiErrorCode {
  // Event errors
  EVENT_NOT_FOUND = 'B4001',
  EVENT_ALREADY_DELETED = 'B4002',
  EVENT_INVALID_PIN = 'B4003',

  // Currency errors
  CURRENCY_NOT_FOUND = 'B4004',
  CURRENCY_RATE_NOT_FOUND = 'B4005',

  // Generic errors
  VALIDATION_ERROR = 'B4006',
  INTERNAL_ERROR = 'B4007',

  // Token errors
  INVALID_TOKEN = 'B4008',
  TOKEN_EXPIRED = 'B4009',
}

export function getUserFriendlyMessage(error: ApiError): string {
  const e = languageStore.content.Errors;

  const messages: Record<string, string> = {
    [ApiErrorCode.EVENT_NOT_FOUND]: e.eventNotFound,
    [ApiErrorCode.EVENT_ALREADY_DELETED]: e.eventDeleted,
    [ApiErrorCode.EVENT_INVALID_PIN]: e.invalidPin,
    [ApiErrorCode.CURRENCY_NOT_FOUND]: e.currencyNotFound,
    [ApiErrorCode.CURRENCY_RATE_NOT_FOUND]: e.exchangeRateNotFound,
    [ApiErrorCode.VALIDATION_ERROR]: e.validationError,
    [ApiErrorCode.INTERNAL_ERROR]: e.serverError,
    [ApiErrorCode.INVALID_TOKEN]: e.invalidToken,
    [ApiErrorCode.TOKEN_EXPIRED]: e.tokenExpired,
  };

  return messages[error.code] || error.message || e.unexpected;
}
