import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

import {
  FormationCourse,
  FormationCourseDetail,
  FormationLesson,
  MessageResponse,
  QuizResult
} from '../models/formation.models';


@Injectable({
  providedIn: 'root'
})
export class FormationService {

  private readonly apiUrl =
    'http://localhost:8081/api/formation';


  constructor(
    private readonly http: HttpClient
  ) {}


  getCourses(): Observable<FormationCourse[]> {

    return this.http.get<FormationCourse[]>(
      `${this.apiUrl}/courses`,
      {
        withCredentials: true
      }
    );
  }


  getCourse(
    courseId: number
  ): Observable<FormationCourseDetail> {

    return this.http.get<FormationCourseDetail>(
      `${this.apiUrl}/courses/${courseId}`,
      {
        withCredentials: true
      }
    );
  }


  getLesson(
    lessonId: number
  ): Observable<FormationLesson> {

    return this.http.get<FormationLesson>(
      `${this.apiUrl}/lessons/${lessonId}`,
      {
        withCredentials: true
      }
    );
  }


  completeLesson(
    lessonId: number
  ): Observable<MessageResponse> {

    return this.http.post<MessageResponse>(
      `${this.apiUrl}/lessons/${lessonId}/complete`,
      {},
      {
        withCredentials: true
      }
    );
  }


  submitQuiz(
    lessonId: number,
    answers: (number | null)[]
  ): Observable<QuizResult> {

    return this.http.post<QuizResult>(
      `${this.apiUrl}/lessons/${lessonId}/quiz`,
      { answers },
      {
        withCredentials: true
      }
    );
  }
}
