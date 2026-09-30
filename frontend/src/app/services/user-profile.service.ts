import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface UserProfile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string;
  phoneNumber: string;
  role: string;
  enabled: boolean;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string;
  countryCode: string;
  phoneNumber: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserProfileService {

  private readonly apiUrl =
    'http://localhost:8081/api/users';

  constructor(
    private readonly http: HttpClient
  ) {}

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(
      `${this.apiUrl}/me`,
      {
        withCredentials: true
      }
    );
  }

  updateProfile(
    request: UpdateProfileRequest
  ): Observable<UserProfile> {

    return this.http.put<UserProfile>(
      `${this.apiUrl}/me`,
      request,
      {
        withCredentials: true
      }
    );
  }
}