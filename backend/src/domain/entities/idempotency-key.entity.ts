export interface IIdempotencyKey {
  key: string;
  url: string;
  requestHash: string;
  response: object;
  statusCode: number;
  expiresAt: Date;
  createdAt: Date;
}
