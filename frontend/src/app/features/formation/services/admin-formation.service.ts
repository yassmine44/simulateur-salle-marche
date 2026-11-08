import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable, map } from 'rxjs';

import {
  AdminCourse,
  AdminLesson,
  RawAdminCourse
} from '../models/admin-formation.models';


@Injectable({
  providedIn: 'root'
})
export class AdminFormationService {

  private readonly apiUrl =
    'http://localhost:8081/api/admin/formation';

  private readonly options = {
    withCredentials: true
  };


  constructor(
    private readonly http: HttpClient
  ) {}


  // =========================
  // FORMATIONS
  // =========================

  listCourses(): Observable<AdminCourse[]> {

    return this.http
      .get<RawAdminCourse[]>(
        `${this.apiUrl}/courses`,
        this.options
      )
      .pipe(
        map(courses => courses.map(c => this.toCourse(c)))
      );
  }


  getCourse(courseId: number): Observable<AdminCourse> {

    return this.http
      .get<RawAdminCourse>(
        `${this.apiUrl}/courses/${courseId}`,
        this.options
      )
      .pipe(map(c => this.toCourse(c)));
  }


  createCourse(course: AdminCourse): Observable<AdminCourse> {

    return this.http
      .post<RawAdminCourse>(
        `${this.apiUrl}/courses`,
        this.coursePayload(course),
        this.options
      )
      .pipe(map(c => this.toCourse(c)));
  }


  updateCourse(course: AdminCourse): Observable<AdminCourse> {

    return this.http
      .put<RawAdminCourse>(
        `${this.apiUrl}/courses/${course.id}`,
        this.coursePayload(course),
        this.options
      )
      .pipe(map(c => this.toCourse(c)));
  }


  deleteCourse(courseId: number): Observable<void> {

    return this.http.delete<void>(
      `${this.apiUrl}/courses/${courseId}`,
      this.options
    );
  }


  // =========================
  // LEÇONS (renvoient la formation complète)
  // =========================

  createLesson(
    courseId: number,
    lesson: AdminLesson
  ): Observable<AdminCourse> {

    return this.http
      .post<RawAdminCourse>(
        `${this.apiUrl}/courses/${courseId}/lessons`,
        this.lessonPayload(lesson),
        this.options
      )
      .pipe(map(c => this.toCourse(c)));
  }


  updateLesson(lesson: AdminLesson): Observable<AdminCourse> {

    return this.http
      .put<RawAdminCourse>(
        `${this.apiUrl}/lessons/${lesson.id}`,
        this.lessonPayload(lesson),
        this.options
      )
      .pipe(map(c => this.toCourse(c)));
  }


  deleteLesson(lessonId: number): Observable<AdminCourse> {

    return this.http
      .delete<RawAdminCourse>(
        `${this.apiUrl}/lessons/${lessonId}`,
        this.options
      )
      .pipe(map(c => this.toCourse(c)));
  }


  // =========================
  // CONVERSIONS
  // =========================

  private coursePayload(course: AdminCourse) {

    return {
      title: course.title.trim(),
      description: course.description.trim(),
      level: course.level,
      category: course.category.trim(),
      displayOrder: course.displayOrder,
      published: course.published
    };
  }


  private lessonPayload(lesson: AdminLesson) {

    return {
      title: lesson.title.trim(),
      content: lesson.content,
      durationMinutes: lesson.durationMinutes,
      displayOrder: lesson.displayOrder,
      videoUrl: lesson.videoUrl.trim(),
      quiz: lesson.quiz.map(question => ({
        question: question.question.trim(),
        options: question.options.map(option => option.trim()),
        correctIndex: question.correctIndex,
        explanation: question.explanation.trim()
      }))
    };
  }


  private toCourse(raw: RawAdminCourse): AdminCourse {

    return {
      id: raw.id,
      title: raw.title,
      description: raw.description,
      level: raw.level,
      category: raw.category,
      displayOrder: raw.displayOrder,
      published: raw.published,
      lessonCount: raw.lessonCount,
      lessons: raw.lessons.map(lesson => ({
        id: lesson.id,
        title: lesson.title,
        content: lesson.content,
        durationMinutes: lesson.durationMinutes,
        displayOrder: lesson.displayOrder,
        videoUrl: lesson.videoUrl ?? '',
        quiz: lesson.quiz.map(question => ({
          question: question.question,
          options: [...question.options],
          correctIndex: question.correctIndex,
          explanation: question.explanation ?? ''
        }))
      }))
    };
  }
}
