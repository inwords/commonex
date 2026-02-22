export const UserRoutes = {
  root: '/user',
  getAllCurrencies: `/currencies/all`,
  createEvent: '/event',
  getEventInfo: `/event/:eventId`,
  deleteEvent: `/event/:eventId`,
  addUsersToEvent: `/event/:eventId/users`,
  getAllEventExpenses: `/event/:eventId/expenses`,
  createExpense: `/event/:eventId/expense`,
};

export const UserV2Routes = {
  root: '/v2/user',
  getEventInfo: `/event/:eventId`,
  addUsersToEvent: `/event/:eventId/users`,
  getAllEventExpenses: `/event/:eventId/expenses`,
  createExpense: `/event/:eventId/expense`,
  createShareToken: `/event/:eventId/share-token`,
};

export const UserV3Routes = {
  root: '/v3/user',
  getAllCurrencies: `/currencies/all`,
};
