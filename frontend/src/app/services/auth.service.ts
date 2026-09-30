import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  countryCode: string;
  phoneNumber: string;
  email: string;
  password: string;
}

export interface UserResponse {
  id: number;
  firstName: string;
  lastName: string;
  countryCode: string;
  phoneNumber: string;
  email: string;
  role: string;
  enabled: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  phoneNumber: string;
  role: string;
}

export interface CsrfResponse {
  token: string;
  parameterName: string;
  headerName: string;
}
export interface MessageResponse {
  message: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly apiUrl = 'http://localhost:8081/api/auth';

  constructor(private readonly http: HttpClient) {}

  register(request: RegisterRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>(
      `${this.apiUrl}/register`,
      request,
      {
        withCredentials: true
      }
    );
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      request,
      {
        withCredentials: true
      }
    );
  }

  me(): Observable<LoginResponse> {
    return this.http.get<LoginResponse>(
      `${this.apiUrl}/me`,
      {
        withCredentials: true
      }
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}/logout`,
      {},
      {
        withCredentials: true
      }
    );
  }

  csrf(): Observable<CsrfResponse> {
    return this.http.get<CsrfResponse>(
      'http://localhost:8081/api/csrf',
      {
        withCredentials: true
      }
    );
  }
  forgotPassword(
  request: ForgotPasswordRequest
): Observable<MessageResponse> {
  return this.http.post<MessageResponse>(
    `${this.apiUrl}/forgot-password`,
    request
  );
}

resetPassword(
  request: ResetPasswordRequest
): Observable<MessageResponse> {
  return this.http.post<MessageResponse>(
    `${this.apiUrl}/reset-password`,
    request
  );
}
}