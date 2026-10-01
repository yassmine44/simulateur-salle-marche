import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';
import { adminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';
import { AuthStateService } from '../services/auth-state.service';

describe('adminGuard', () => {
  const auth = { me: jasmine.createSpy('me') };
  const state = { setUser: jasmine.createSpy('setUser'), clearUser: jasmine.createSpy('clearUser') };
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: AuthService, useValue: auth }, { provide: AuthStateService, useValue: state }] });
    state.setUser.calls.reset(); state.clearUser.calls.reset();
  });
  async function run() {
    return firstValueFrom(TestBed.runInInjectionContext(() => adminGuard(
      {} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)) as Observable<any>);
  }
  it('restores an ADMIN session from the API', async () => {
    const user = { id: 7, role: 'ADMIN' }; auth.me.and.returnValue(of(user));
    expect(await run()).toBeTrue(); expect(state.setUser).toHaveBeenCalledWith(user);
  });
  it('redirects USER to dashboard', async () => {
    auth.me.and.returnValue(of({ role: 'USER' }));
    expect(TestBed.inject(Router).serializeUrl(await run())).toBe('/app/dashboard');
  });
  it('clears expired sessions and redirects to login', async () => {
    auth.me.and.returnValue(throwError(() => ({ status: 401 })));
    expect(TestBed.inject(Router).serializeUrl(await run())).toBe('/login');
    expect(state.clearUser).toHaveBeenCalled();
  });
});
