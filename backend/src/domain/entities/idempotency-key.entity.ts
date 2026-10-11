export interface IIdempotencyKey {
  key: string;
  operationId: string;
  requestHash: string;
  response: object;
  responseVersion: number;
  expiresAt: Date;
  createdAt: Date;
}
