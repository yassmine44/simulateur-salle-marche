import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService, RegisterRequest } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the registration request without confirmPassword', () => {
    const request: RegisterRequest = {
      firstName: 'Test',
      lastName: 'Angular',
      email: 'test.angular@example.com',
      password: 'Password123!'
    };

    service.register(request).subscribe(user => {
      expect(user.email).toBe(request.email);
      expect(user.role).toBe('USER');
    });

    const pending = http.expectOne('http://localhost:8081/api/auth/register');
    expect(pending.request.method).toBe('POST');
    expect(pending.request.body).toEqual(request);
    expect(pending.request.body.confirmPassword).toBeUndefined();
    pending.flush({ ...request, id: 1, role: 'USER', enabled: true });
  });
});
