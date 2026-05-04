export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface CurrentUser {
  userId: string;
  tenantId: string | null;
  email: string;
  preferredLanguage: string;
  roles: string[];
  permissions: string[];
}
