import { HttpInterceptorFn } from '@angular/common/http';

const MUTATING_METHODS = [
  'POST',
  'PUT',
  'PATCH',
  'DELETE'
];

export const csrfInterceptor: HttpInterceptorFn = (request, next) => {

  const methodRequiresCsrf =
    MUTATING_METHODS.includes(request.method.toUpperCase());

  if (!methodRequiresCsrf) {
    return next(request);
  }

  const token = getCookie('XSRF-TOKEN');

  if (!token) {
    return next(request);
  }

  const securedRequest = request.clone({
    setHeaders: {
      'X-XSRF-TOKEN': decodeURIComponent(token)
    }
  });

  return next(securedRequest);
};


function getCookie(name: string): string | null {

  const cookies = document.cookie
    .split(';')
    .map(cookie => cookie.trim());

  for (const cookie of cookies) {

    if (cookie.startsWith(`${name}=`)) {
      return cookie.substring(name.length + 1);
    }
  }

  return null;
}