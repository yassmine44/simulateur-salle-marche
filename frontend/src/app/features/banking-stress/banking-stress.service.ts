import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  BankingStressTestRequest,
  BankingStressTestResponse
} from './banking-stress.model';

@Injectable({ providedIn: 'root' })
export class BankingStressService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = 'http://localhost:8081/api/banking-stress-tests/analyze';

  analyze(request: BankingStressTestRequest): Observable<BankingStressTestResponse> {
    return this.http.post<BankingStressTestResponse>(this.endpoint, request, {
      withCredentials: true
    });
  }
}
