import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, map, of, tap } from 'rxjs';

import {
  AuthService,
  LoginResponse
} from './auth.service';

@Injectable({
  providedIn: 'root'
})
export class AuthStateService {

  private readonly authService = inject(AuthService);

  private readonly currentUserSubject =
    new BehaviorSubject<LoginResponse | null>(null);

  readonly currentUser$ =
    this.currentUserSubject.asObservable();

  get currentUser(): LoginResponse | null {
    return this.currentUserSubject.value;
  }

  setUser(user: LoginResponse): void {
    this.currentUserSubject.next(user);
  }

  clearUser(): void {
    this.currentUserSubject.next(null);
  }

  restoreSession(): Observable<boolean> {

    if (this.currentUser) {
      return of(true);
    }

    return this.authService.me().pipe(

      tap(user => {
        this.setUser(user);
      }),

      map(() => true),

      catchError(() => {
        this.clearUser();
        return of(false);
      })
    );
  }
}