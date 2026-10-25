import type {Locale} from './ru';

const en: Locale = {
  FindEventButton: {
    btn: 'Find trip',
  },
  CreateEventButton: {
    btn: 'Create trip',
  },
  Validation: {required: 'This field is required'},
  Settings: {
    title: 'Settings',
    close: 'Close settings',
    language: 'Language',
    darkTheme: 'Dark theme',
  },
  Analytics: {
    title: 'Website analytics',
    settings: 'Analytics settings',
    description:
      'Analytics is enabled by default. Yandex Metrica collects visits and actions to help us improve CommonEx. Trip and expense contents are not sent. You can disable analytics at any time.',
    privacy: 'Privacy policy',
    allow: 'Enable',
    decline: 'Disable',
  },
  AppFooter: {
    title: 'CommonEx on your phone',
    appStore: 'Download on the App Store',
    ruStore: 'Download from RuStore',
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
    skip: 'Skip tutorial',
    restart: 'How to use',
    entry: {
      title: 'Start with a trip',
      content:
        'Create a trip for your group, or enter its ID and PIN if you have been invited. We will then help you record your first shared expense.',
    },
    joinHint: 'Ask the organiser for the ID and PIN. Only share access with trip participants.',
    createEvent: {
      title: 'Bring your group together',
      content:
        'Name your trip, choose a PIN, add yourself and the people sharing expenses, and select a common currency. Then create the trip. Do not enter passwords from other services or card details.',
    },
    selectUser: {
      title: 'Who are you on this trip?',
      content: 'Select your name from the list. If you are not listed yet, choose “Add participant”.',
    },
    addExpense: {
      title: 'Record your first shared expense',
      content:
        'Press “+” when you or someone in the group has paid a shared bill. If there are no expenses yet, you can skip the tutorial.',
    },
    openExpenses: {
      title: 'Back to expenses',
      content: 'Open “My expenses” to add your first expense using the “+” button.',
    },
    expense: {
      amount: {
        title: 'What did you pay for?',
        content: 'Enter a description and the actual expense amount. Select the currency you paid in.',
      },
      owner: {
        title: 'Who paid the bill?',
        content: 'Select the person who actually paid. This may be you or another participant.',
      },
      split: {
        title: 'Who shares this expense?',
        content:
          '“Equally” splits the expense between everyone on the trip. Use a manual split or percentages for a different group or shares.',
      },
      save: {
        title: 'Ready to save',
        content:
          'Check the amount, payer and shares, then press “Add expense”. After it is saved, we will look at debts.',
      },
    },
    debts: {
      title: 'Your first expense is saved!',
      content:
        'Open “My debts” to see whom you owe. If you paid for everyone, debts owed to you appear under “My income”.',
    },
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
