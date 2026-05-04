import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AUTH_SERVICE } from './auth.port';

export function permissionGuard(...required: string[]): CanActivateFn {
  return () => {
    const auth = inject(AUTH_SERVICE);
    const router = inject(Router);
    if (required.every((p) => auth.hasPermission(p))) return true;
    return router.createUrlTree(['/forbidden']);
  };
}

export function roleGuard(...allowed: string[]): CanActivateFn {
  return () => {
    const auth = inject(AUTH_SERVICE);
    const router = inject(Router);
    if (auth.hasAnyRole(...allowed)) return true;
    return router.createUrlTree(['/forbidden']);
  };
}
