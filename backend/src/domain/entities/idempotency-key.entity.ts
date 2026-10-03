export interface IIdempotencyKey {
  key: string;
  /** Legacy fields kept during the expand/contract rollout. */
  url: string | null;
  legacyRequestHash: string | null;
  legacyResponse: object | null;
  statusCode: number | null;
  /** Versioned fields used by the new implementation. */
  operationId: string | null;
  requestHash: string | null;
  response: object | null;
  responseVersion: number | null;
  expiresAt: Date;
  createdAt: Date;
}
