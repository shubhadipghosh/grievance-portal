import { HttpInterceptorFn } from '@angular/common/http';

/** Sends the session cookie. Required whenever the API is on another origin. */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ withCredentials: true }));
