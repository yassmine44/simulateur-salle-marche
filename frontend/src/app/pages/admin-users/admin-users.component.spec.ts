import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AdminUsersComponent } from './admin-users.component';
import { AdminUser, AdminUserService } from '../../services/admin-user.service';
import { AuthService } from '../../services/auth.service';
import { AuthStateService } from '../../services/auth-state.service';

describe('AdminUsersComponent', () => {
  let component: AdminUsersComponent;
  let api: jasmine.SpyObj<AdminUserService>;
  let auth: jasmine.SpyObj<AuthService>;
  const user = { id: 2, firstName: 'Test', lastName: 'User', role: 'USER', enabled: true } as AdminUser;
  beforeEach(() => {
    api = jasmine.createSpyObj('AdminUserService', ['getUsers', 'getUserById', 'updateRole', 'updateStatus']);
    auth = jasmine.createSpyObj('AuthService', ['csrf']);
    api.getUsers.and.returnValue(of({ content: [], number: 0, totalPages: 0, totalElements: 0 } as any));
    auth.csrf.and.returnValue(of({} as any));
    api.updateStatus.and.returnValue(of({ ...user, enabled: false }));
    api.updateRole.and.returnValue(of({ ...user, role: 'ADMIN' }));
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: AdminUserService, useValue: api }, { provide: AuthService, useValue: auth },
      { provide: AuthStateService, useValue: { currentUser: { id: 7 } } }] });
    component = TestBed.runInInjectionContext(() => new AdminUsersComponent());
  });
  afterEach(() => component.ngOnDestroy());
  it('resets pagination and preserves the disabled filter', () => {
    component.page = 4; component.enabled = false; component.applyFilters();
    expect(api.getUsers).toHaveBeenCalledWith(jasmine.objectContaining({ page: 0, enabled: false }));
  });
  it('blocks both mutations of the current ADMIN', () => {
    const self = { ...user, id: 7, role: 'ADMIN' as const };
    component.toggleStatus(self); component.changeRole(self, 'USER');
    expect(auth.csrf).not.toHaveBeenCalled();
  });
  it('does not mutate when confirmation is cancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false); component.toggleStatus(user);
    expect(api.updateStatus).not.toHaveBeenCalled();
  });
  it('initializes CSRF before changing status and refreshes the list', () => {
    spyOn(window, 'confirm').and.returnValue(true); component.toggleStatus(user);
    expect(auth.csrf).toHaveBeenCalled(); expect(api.updateStatus).toHaveBeenCalledWith(2, false);
    expect(api.getUsers).toHaveBeenCalled(); expect(component.busy).toBeFalse();
  });
  it('changes role after confirmation', () => {
    spyOn(window, 'confirm').and.returnValue(true); component.changeRole(user, 'ADMIN');
    expect(api.updateRole).toHaveBeenCalledWith(2, 'ADMIN');
  });
  it('loads user details', () => {
    api.getUserById.and.returnValue(of(user)); component.details(user);
    expect(component.selected).toEqual(user); expect(component.detailLoading).toBeFalse();
  });
});
