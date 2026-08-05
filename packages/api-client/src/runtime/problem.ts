/**
 * RFC 9457 Problem envelope. Mirrors the OpenAPI schema in
 * `contracts/openapi/common/problem-details.yaml`. The runtime
 * never invents new fields; consumers can rely on the shape.
 */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  correlationId?: string;
  errorCode?: string;
  retryAfterSeconds?: number;
}

/**
 * Thrown by `BffClient` when the BFF responds with a non-2xx
 * status. Carries the parsed `Problem` body when available so the
 * UI can render localised messages via `errorCode` mapping.
 */
export class ApiError extends Error {
  public readonly status: number;
  public readonly problem: Problem | undefined;
  public readonly correlationId: string;

  constructor(status: number, problem: Problem | undefined, correlationId: string) {
    const title = problem?.title ?? `HTTP ${status}`;
    super(problem?.detail ?? title);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.correlationId = correlationId;
  }
}
