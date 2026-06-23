import { isApiError } from './api.error';

describe('isApiError', () => {
  it('returns true for an object carrying status, code and message', () => {
    expect(isApiError({ status: 400, code: 'VALIDATION', message: 'bad request' })).toBe(true);
  });

  it('returns false for null and shapes missing required fields', () => {
    expect(isApiError(null)).toBe(false);
    expect(isApiError('oops')).toBe(false);
    expect(isApiError({ status: 500 })).toBe(false);
  });
});
