import {CleanupResult} from './cleanup-result';

export const EXPIRED_RECORDS_BATCH_SIZE = 500;

type DeleteExpiredBatch = (input: {
  expiresBefore: Date;
  limit: number;
}) => Promise<[deletedCount: number, queryDetails: unknown]>;

export const deleteExpiredInBatches = async (deleteBatch: DeleteExpiredBatch): Promise<CleanupResult> => {
  const expiresBefore = new Date();
  let deletedCount = 0;
  let batchCount = 0;
  let batchDeletedCount: number;

  do {
    [batchDeletedCount] = await deleteBatch({expiresBefore, limit: EXPIRED_RECORDS_BATCH_SIZE});
    deletedCount += batchDeletedCount;
    if (batchDeletedCount > 0) batchCount += 1;
  } while (batchDeletedCount === EXPIRED_RECORDS_BATCH_SIZE);

  return {deletedCount, batchCount};
};
