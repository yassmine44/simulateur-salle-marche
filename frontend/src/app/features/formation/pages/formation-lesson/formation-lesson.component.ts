import {
  Component,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Subscription } from 'rxjs';

import {
  FormationLesson,
  LessonBlock,
  QuizResult,
  parseLessonContent,
  youtubeVideoId
} from '../../models/formation.models';

import { FormationService }
  from '../../services/formation.service';


@Component({
  selector: 'app-formation-lesson',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './formation-lesson.component.html',
  styleUrl: './formation-lesson.component.scss'
})
export class FormationLessonComponent
  implements OnInit, OnDestroy {

  private readonly route = inject(ActivatedRoute);

  private readonly formationService =
    inject(FormationService);

  private readonly sanitizer = inject(DomSanitizer);

  private routeSubscription?: Subscription;

  lesson: FormationLesson | null = null;

  blocks: LessonBlock[] = [];

  // URL d'intégration YouTube (construite à partir de l'identifiant validé).
  videoUrl: SafeResourceUrl | null = null;

  // Une réponse (index de l'option) par question ; null = pas répondu.
  answers: (number | null)[] = [];

  result: QuizResult | null = null;

  loading = true;

  busy = false;

  errorMessage = '';


  ngOnInit(): void {

    this.routeSubscription =
      this.route.paramMap.subscribe(params => {
        this.load(Number(params.get('id')));
      });
  }


  ngOnDestroy(): void {
    this.routeSubscription?.unsubscribe();
  }


  get hasQuiz(): boolean {
    return !!this.lesson && this.lesson.quiz.length > 0;
  }


  get allAnswered(): boolean {

    return this.answers.length > 0
      && this.answers.every(answer => answer !== null);
  }


  select(questionIndex: number, optionIndex: number): void {

    if (this.result || this.busy) {
      return;
    }

    this.answers[questionIndex] = optionIndex;
  }


  isCorrectOption(
    questionIndex: number,
    optionIndex: number
  ): boolean {

    return !!this.result
      && this.result.results[questionIndex]?.correctIndex
        === optionIndex;
  }


  isWrongOption(
    questionIndex: number,
    optionIndex: number
  ): boolean {

    return !!this.result
      && this.answers[questionIndex] === optionIndex
      && !this.result.results[questionIndex]?.correct;
  }


  submitQuiz(): void {

    if (!this.lesson || !this.allAnswered || this.busy) {
      return;
    }

    this.busy = true;
    this.errorMessage = '';

    this.formationService
      .submitQuiz(this.lesson.id, this.answers)
      .subscribe({

        next: (result) => {

          this.result = result;

          if (this.lesson && result.lessonCompleted) {
            this.lesson.completed = true;
            this.lesson.bestScorePercent = Math.max(
              this.lesson.bestScorePercent ?? 0,
              result.scorePercent
            );
          }

          this.busy = false;
        },

        error: (error) => {
          this.errorMessage = this.extractError(
            error,
            'Impossible de corriger le quiz. Réessayez.'
          );
          this.busy = false;
        }
      });
  }


  retryQuiz(): void {

    this.result = null;

    this.answers = this.lesson
      ? this.lesson.quiz.map(() => null)
      : [];
  }


  completeLesson(): void {

    if (!this.lesson || this.busy) {
      return;
    }

    this.busy = true;
    this.errorMessage = '';

    this.formationService
      .completeLesson(this.lesson.id)
      .subscribe({

        next: () => {

          if (this.lesson) {
            this.lesson.completed = true;
          }

          this.busy = false;
        },

        error: (error) => {
          this.errorMessage = this.extractError(
            error,
            'Impossible de valider la leçon. Réessayez.'
          );
          this.busy = false;
        }
      });
  }


  private load(lessonId: number): void {

    this.loading = true;
    this.errorMessage = '';
    this.result = null;

    if (!Number.isFinite(lessonId)) {
      this.lesson = null;
      this.errorMessage = 'Leçon introuvable.';
      this.loading = false;
      return;
    }

    this.formationService
      .getLesson(lessonId)
      .subscribe({

        next: (lesson) => {

          this.lesson = lesson;
          this.blocks = parseLessonContent(lesson.content);
          this.videoUrl = this.buildVideoUrl(lesson.videoUrl);
          this.answers = lesson.quiz.map(() => null);
          this.loading = false;

          if (typeof window !== 'undefined') {
            window.scrollTo({ top: 0 });
          }
        },

        error: () => {
          this.lesson = null;
          this.errorMessage = 'Leçon introuvable.';
          this.loading = false;
        }
      });
  }


  private buildVideoUrl(raw: string | null): SafeResourceUrl | null {

    const id = youtubeVideoId(raw);

    // L'identifiant ne contient que [A-Za-z0-9_-] : l'URL est sûre.
    return id
      ? this.sanitizer.bypassSecurityTrustResourceUrl(
        `https://www.youtube-nocookie.com/embed/${id}`
      )
      : null;
  }


  private extractError(
    error: unknown,
    fallback: string
  ): string {

    const httpError = error as {
      error?: { detail?: string; message?: string };
    };

    return httpError?.error?.detail
      || httpError?.error?.message
      || fallback;
  }
}
