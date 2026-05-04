export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId?: string;
  fieldErrors?: Array<{ field: string; code: string; message: string }>;
  details?: Record<string, unknown>;
}

export function isApiError(value: unknown): value is ApiError {
  return (
    typeof value === 'object' &&
    value !== null &&
    'status' in value &&
    'code' in value &&
    'message' in value
  );
}
