import type {Locale} from './ru';

const en: Locale = {
  FindEventButton: {
    btn: 'Find trip',
  },
  CreateEventButton: {
    btn: 'Create trip',
  },
  MainPage: {
    subtitle:
      'A convenient service for tracking shared expenses in trips and events. Create events, add participants, and track who owes whom.',
  },
  FindEvent: {
    eventIdInput: 'Trip ID',
  },
  CreateEvent: {
    modal: {title: 'Create trip'},
    form: {
      submit: 'Create trip',
      eventNameInput: 'Trip name',
      pinCodeInput: 'Trip PIN code. Required to access the trip',
      userNameInput: 'Name',
      addUser: 'Add participant',
    },
  },
  SelectUser: {
    title: 'Select participant',
    label: 'Participant',
  },
  EventHeader: {
    totalSpent: 'Total spent:',
    youSpent: 'You spent:',
    copyLink: 'Copy trip link',
    linkCopied: 'Link copied! Do not share this link with strangers. The link will be active for 14 days.',
  },
  EventTabs: {
    myExpenses: 'My expenses',
    allExpenses: 'All expenses',
    myDebts: 'My debts',
    myIncome: 'Owed to me',
  },
  AddExpense: {
    modalTitle: 'Add expense',
    descriptionInput: 'Description',
    amountInput: 'Amount',
    ownerLabel: 'Paid by',
    splitOption: {
      label: 'Split method',
      equal: 'Equally',
      manual: 'Manual',
      percentage: 'By percentage',
    },
    splitByPercentage: {
      percentLabel: 'Percentage',
      onlyIntegers: 'Integers only',
      participantLabel: 'Participant',
      remove: 'Remove',
      addParticipant: 'Add participant',
    },
    manualSplit: {
      amountLabel: 'Amount owed',
      whoOwesLabel: 'Who owes',
      remove: 'Remove',
      addPerson: 'Add person',
    },
    submit: 'Add expense',
  },
  AddExpenseRefund: {
    modalTitle: 'Refund expense',
    descriptionInput: 'Refund description',
    amountInput: 'Refund amount',
    ownerLabel: 'Who pays back',
    receiverLabel: 'Who receives',
    refundPrefix: 'Refund for',
    refundDebtPrefix: 'Debt repayment to',
  },
  ExpenseDetails: {
    modalTitle: 'Expense details',
  },
  ExpensesList: {
    paidBy: 'Paid by:',
    unknown: 'Unknown',
    currency: 'Expense currency:',
    manual: '(manual)',
    refund: 'Refund',
    editedOn: (date: string) => `Edited on ${date}`,
    revertedOn: (date: string) => `Reverted on ${date}`,
  },
  DebtsList: {
    noDebts: 'You have no debts',
    yourDebt: 'Your debt',
    refund: 'Repay',
  },
  AddUsers: {
    modalTitle: 'Add participants',
    addBtn: 'Add participant',
    submit: 'Submit',
  },
  Currency: {
    label: 'Currency',
  },
  ExchangeRate: {
    label: 'Exchange rate',
  },
  PinCode: {
    label: 'Trip PIN:',
  },
  Onboarding: {
    step: 'Step',
    of: 'of',
    back: 'Back',
    next: 'Next',
    done: 'Got it',
    main: [
      {
        title: 'Event ID',
        content:
          'The event ID is a unique identifier for your event. You will receive it after creating an event. Use it for quick access to the event.',
      },
      {
        title: 'PIN code',
        content:
          'The PIN code is a security code to access the event. Share it only with your event participants. Without the PIN, no one can view or modify expense data.',
      },
      {
        title: 'Data security',
        content:
          '⚠️ Important: Do not store sensitive data, card numbers, passwords, or other confidential information in the service. It is intended only for tracking shared expenses.',
      },
    ],
    event: [
      {
        title: 'Event tabs',
        content:
          'Use tabs to navigate: "My expenses" - your expenses only, "All expenses" - all event expenses, "My debts" - who you owe, "Owed to me" - who owes you.',
      },
      {
        title: 'Adding expenses',
        content:
          'Click "Add expense" to record a new expense. Enter a description, amount, currency, and select participants to split with.',
      },
      {
        title: 'Viewing debts',
        content:
          'In the "My debts" tab you will see who you owe and how much. Click "Repay" to record a debt repayment.',
      },
      {
        title: 'Switch user',
        content:
          'Click the avatar in the top right corner to switch users. This is useful when multiple participants share one device.',
      },
    ],
  },
  Errors: {
    eventNotFound: 'Event not found. Check the event ID.',
    eventDeleted: 'Event has already been deleted.',
    invalidPin: 'Invalid PIN code. Check your input.',
    currencyNotFound: 'Currency not found.',
    exchangeRateNotFound: 'Exchange rate not found. Try selecting a different currency.',
    validationError: 'Validation error. Check your input.',
    serverError: 'A server error occurred. Please try again later.',
    invalidToken: 'Invalid token. Please sign in again.',
    tokenExpired: 'Token expired. Please sign in again.',
    unexpected: 'An unexpected error occurred.',
  },
  Support: {
    title: 'Support',
    subtitle: 'If you have any questions or issues — contact us.',
    contacts: 'Contact',
    email: 'commonex@proton.me',
    documents: 'Documents',
    privacyPolicy: 'Privacy Policy',
    privacyPolicyUrl: '/privacy.html',
    faq: {
      title: 'FAQ',
      items: [
        {
          q: 'How do I join an existing event?',
          a: 'On the main page, enter the event ID and PIN code — they are provided when an event is created. Share them with participants.',
        },
        {
          q: 'Do I need to register?',
          a: 'No. CommonEx does not require an account. Access to an event is via ID and PIN code.',
        },
        {
          q: 'How do I add an expense?',
          a: 'Open the event, select your name, go to the "Expenses" tab and tap "+".',
        },
        {
          q: 'How do I see who owes whom?',
          a: 'In the event, open the "Debts" tab — it shows the final balance between participants.',
        },
        {
          q: 'Is data stored forever?',
          a: 'Events with no activity are deleted after 6 months. Active events are stored indefinitely.',
        },
        {
          q: 'Is the app free?',
          a: 'Yes, CommonEx is completely free.',
        },
      ],
    },
  },
};

export default en;
