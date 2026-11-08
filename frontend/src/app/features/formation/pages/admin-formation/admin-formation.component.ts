import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  AdminCourse,
  AdminLesson,
  AdminQuizQuestion
} from '../../models/admin-formation.models';

import {
  COURSE_LEVEL_LABELS,
  CourseLevel,
  youtubeVideoId
} from '../../models/formation.models';

import { AdminFormationService }
  from '../../services/admin-formation.service';


const MAX_OPTIONS = 6;

const MIN_OPTIONS = 2;


@Component({
  selector: 'app-admin-formation',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink
  ],
  templateUrl: './admin-formation.component.html',
  styleUrl: './admin-formation.component.scss'
})
export class AdminFormationComponent implements OnInit {

  private readonly adminService =
    inject(AdminFormationService);

  readonly levelLabels = COURSE_LEVEL_LABELS;

  readonly levels: { key: CourseLevel; label: string }[] = [
    { key: 'BEGINNER', label: COURSE_LEVEL_LABELS.BEGINNER },
    { key: 'INTERMEDIATE', label: COURSE_LEVEL_LABELS.INTERMEDIATE },
    { key: 'ADVANCED', label: COURSE_LEVEL_LABELS.ADVANCED }
  ];

  courses: AdminCourse[] = [];

  // Formation en cours d'édition (null = on affiche la liste).
  editing: AdminCourse | null = null;

  // Leçon en cours d'édition (null = formulaire fermé).
  lessonForm: AdminLesson | null = null;

  loading = true;

  busy = false;

  errorMessage = '';

  successMessage = '';


  ngOnInit(): void {
    this.loadCourses();
  }


  // =========================
  // LISTE
  // =========================

  loadCourses(): void {

    this.adminService
      .listCourses()
      .subscribe({

        next: (courses) => {
          this.courses = courses;
          this.loading = false;
        },

        error: (error) => {
          this.errorMessage = this.extractError(
            error,
            'Impossible de charger les formations.'
          );
          this.loading = false;
        }
      });
  }


  // =========================
  // FORMATION
  // =========================

  newCourse(): void {

    this.clearMessages();
    this.lessonForm = null;

    const nextOrder = this.courses.reduce(
      (max, course) => Math.max(max, course.displayOrder),
      0
    ) + 1;

    this.editing = {
      id: null,
      title: '',
      description: '',
      level: 'BEGINNER',
      category: '',
      displayOrder: nextOrder,
      published: true,
      lessonCount: 0,
      lessons: []
    };
  }


  editCourse(course: AdminCourse): void {

    if (course.id === null) {
      return;
    }

    this.clearMessages();
    this.lessonForm = null;
    this.busy = true;

    this.adminService
      .getCourse(course.id)
      .subscribe({

        next: (full) => {
          this.editing = full;
          this.busy = false;
        },

        error: (error) => {
          this.errorMessage = this.extractError(
            error,
            'Impossible de charger la formation.'
          );
          this.busy = false;
        }
      });
  }


  backToList(): void {

    this.editing = null;
    this.lessonForm = null;
    this.clearMessages();
    this.loadCourses();
  }


  saveCourse(): void {

    const course = this.editing;

    if (!course || this.busy) {
      return;
    }

    const problem = this.validateCourse(course);

    if (problem) {
      this.errorMessage = problem;
      return;
    }

    this.clearMessages();
    this.busy = true;

    const isNew = course.id === null;

    const request = isNew
      ? this.adminService.createCourse(course)
      : this.adminService.updateCourse(course);

    request.subscribe({

      next: (saved) => {

        this.editing = saved;

        this.successMessage = isNew
          ? 'Formation créée. Ajoutez maintenant ses leçons.'
          : 'Formation enregistrée.';

        this.busy = false;
      },

      error: (error) => {
        this.errorMessage = this.extractError(
          error,
          'Impossible d’enregistrer la formation.'
        );
        this.busy = false;
      }
    });
  }


  deleteCourse(course: AdminCourse): void {

    if (course.id === null || this.busy) {
      return;
    }

    const confirmed = this.confirmAction(
      `Supprimer la formation « ${course.title} » `
      + 'avec toutes ses leçons et la progression associée ?'
    );

    if (!confirmed) {
      return;
    }

    const courseId = course.id;

    this.clearMessages();
    this.busy = true;

    this.adminService
      .deleteCourse(courseId)
      .subscribe({

        next: () => {
          this.courses = this.courses.filter(c => c.id !== courseId);
          this.successMessage = 'Formation supprimée.';
          this.busy = false;
        },

        error: (error) => {
          this.errorMessage = this.extractError(
            error,
            'Impossible de supprimer la formation.'
          );
          this.busy = false;
        }
      });
  }


  // =========================
  // LEÇON
  // =========================

  newLesson(): void {

    if (!this.editing) {
      return;
    }

    this.clearMessages();

    const nextOrder = this.editing.lessons.reduce(
      (max, lesson) => Math.max(max, lesson.displayOrder),
      0
    ) + 1;

    this.lessonForm = {
      id: null,
      title: '',
      content: '',
      durationMinutes: 5,
      displayOrder: nextOrder,
      videoUrl: '',
      quiz: []
    };
  }


  editLesson(lesson: AdminLesson): void {

    this.clearMessages();

    // Copie profonde : on n'altère la liste qu'après enregistrement.
    this.lessonForm = {
      ...lesson,
      quiz: lesson.quiz.map(question => ({
        ...question,
        options: [...question.options]
      }))
    };
  }


  cancelLesson(): void {

    this.lessonForm = null;
    this.clearMessages();
  }


  saveLesson(): void {

    const course = this.editing;
    const lesson = this.lessonForm;

    if (!course || course.id === null || !lesson || this.busy) {
      return;
    }

    const problem = this.validateLesson(lesson);

    if (problem) {
      this.errorMessage = problem;
      return;
    }

    this.clearMessages();
    this.busy = true;

    const request = lesson.id === null
      ? this.adminService.createLesson(course.id, lesson)
      : this.adminService.updateLesson(lesson);

    request.subscribe({

      next: (saved) => {
        this.editing = saved;
        this.lessonForm = null;
        this.successMessage = 'Leçon enregistrée.';
        this.busy = false;
      },

      error: (error) => {
        this.errorMessage = this.extractError(
          error,
          'Impossible d’enregistrer la leçon.'
        );
        this.busy = false;
      }
    });
  }


  deleteLesson(lesson: AdminLesson): void {

    if (lesson.id === null || this.busy) {
      return;
    }

    const confirmed = this.confirmAction(
      `Supprimer la leçon « ${lesson.title} » ?`
    );

    if (!confirmed) {
      return;
    }

    this.clearMessages();
    this.busy = true;

    this.adminService
      .deleteLesson(lesson.id)
      .subscribe({

        next: (saved) => {
          this.editing = saved;
          this.lessonForm = null;
          this.successMessage = 'Leçon supprimée.';
          this.busy = false;
        },

        error: (error) => {
          this.errorMessage = this.extractError(
            error,
            'Impossible de supprimer la leçon.'
          );
          this.busy = false;
        }
      });
  }


  // =========================
  // QUIZ
  // =========================

  addQuestion(): void {

    this.lessonForm?.quiz.push({
      question: '',
      options: ['', ''],
      correctIndex: 0,
      explanation: ''
    });
  }


  removeQuestion(index: number): void {
    this.lessonForm?.quiz.splice(index, 1);
  }


  addOption(question: AdminQuizQuestion): void {

    if (question.options.length < MAX_OPTIONS) {
      question.options.push('');
    }
  }


  removeOption(
    question: AdminQuizQuestion,
    optionIndex: number
  ): void {

    if (question.options.length <= MIN_OPTIONS) {
      return;
    }

    question.options.splice(optionIndex, 1);

    if (question.correctIndex === optionIndex) {
      question.correctIndex = 0;
    } else if (question.correctIndex > optionIndex) {
      question.correctIndex--;
    }
  }


  canAddOption(question: AdminQuizQuestion): boolean {
    return question.options.length < MAX_OPTIONS;
  }


  canRemoveOption(question: AdminQuizQuestion): boolean {
    return question.options.length > MIN_OPTIONS;
  }


  // =========================
  // VALIDATION CÔTÉ CLIENT
  // =========================

  private validateCourse(course: AdminCourse): string | null {

    if (!course.title.trim()) {
      return 'Le titre est obligatoire.';
    }

    if (!course.description.trim()) {
      return 'La description est obligatoire.';
    }

    if (!course.category.trim()) {
      return 'La catégorie est obligatoire.';
    }

    if (course.displayOrder === null || course.displayOrder < 0) {
      return 'L’ordre doit être un nombre positif.';
    }

    return null;
  }


  private validateLesson(lesson: AdminLesson): string | null {

    if (!lesson.title.trim()) {
      return 'Le titre de la leçon est obligatoire.';
    }

    if (!lesson.content.trim()) {
      return 'Le contenu de la leçon est obligatoire.';
    }

    if (!lesson.durationMinutes || lesson.durationMinutes < 1) {
      return 'La durée doit être d’au moins 1 minute.';
    }

    if (lesson.displayOrder === null || lesson.displayOrder < 0) {
      return 'L’ordre doit être un nombre positif.';
    }

    if (lesson.videoUrl.trim() && !youtubeVideoId(lesson.videoUrl)) {
      return 'Le lien vidéo doit être un lien YouTube valide '
        + '(ex. https://www.youtube.com/watch?v=XXXXXXXXXXX).';
    }

    for (let i = 0; i < lesson.quiz.length; i++) {

      const question = lesson.quiz[i];

      const label = `Question ${i + 1}`;

      if (!question.question.trim()) {
        return `${label} : l’énoncé est obligatoire.`;
      }

      if (question.options.some(option => !option.trim())) {
        return `${label} : toutes les options doivent être remplies.`;
      }

      if (
        question.correctIndex < 0
        || question.correctIndex >= question.options.length
      ) {
        return `${label} : choisissez la bonne réponse.`;
      }
    }

    return null;
  }


  // =========================
  // OUTILS
  // =========================

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }


  private confirmAction(message: string): boolean {

    return typeof window === 'undefined'
      ? false
      : window.confirm(message);
  }


  private extractError(
    error: unknown,
    fallback: string
  ): string {

    const httpError = error as {
      status?: number;
      error?: {
        detail?: string;
        message?: string;
        errors?: Record<string, string>;
      };
    };

    if (httpError?.status === 403) {
      return 'Accès refusé : réservé aux administrateurs.';
    }

    const fieldErrors = httpError?.error?.errors;

    if (fieldErrors && typeof fieldErrors === 'object') {

      const messages = Object.values(fieldErrors);

      if (messages.length > 0) {
        return messages.join(' ');
      }
    }

    return httpError?.error?.detail
      || httpError?.error?.message
      || fallback;
  }
}
