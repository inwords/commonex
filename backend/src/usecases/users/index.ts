import {GetAllCurrenciesUseCase} from './get-all-currencies.usecase';
import {GetEventInfoUseCase} from './get-event-info.usecase';
import {SaveEventUseCase} from './save-event.usecase';
import {GetEventExpensesUseCase} from './get-event-expenses.usecase';
import {SaveEventExpenseUseCase} from './save-event-expense.usecase';
import {SaveUsersToEventUseCase} from './save-users-to-event.usecase';
import {DeleteEventUseCase} from './delete-event.usecase';
import {CreateEventShareTokenV2UseCase, GetEventExpensesV2UseCase, GetEventInfoV2UseCase, SaveEventExpenseV2UseCase, SaveUsersToEventV2UseCase} from './v2';
import {GetAllCurrenciesWithRatesUseCaseV3, GetCurrenciesV3VersionUseCase} from './v3';

export const allUsersUseCases = [
  GetAllCurrenciesUseCase,
  GetAllCurrenciesWithRatesUseCaseV3,
  GetCurrenciesV3VersionUseCase,
  GetEventInfoUseCase,
  SaveEventUseCase,
  GetEventExpensesUseCase,
  SaveEventExpenseUseCase,
  SaveUsersToEventUseCase,
  DeleteEventUseCase,
  GetEventInfoV2UseCase,
  SaveUsersToEventV2UseCase,
  SaveEventExpenseV2UseCase,
  GetEventExpensesV2UseCase,
  CreateEventShareTokenV2UseCase,
];
