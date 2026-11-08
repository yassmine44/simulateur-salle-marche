import {
  Component,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Subscription } from 'rxjs';

import {
  COURSE_LEVEL_LABELS,
  FormationCourseDetail,
  FormationLessonSummary
} from '../../models/formation.models';

import { FormationService }
  from '../../services/formation.service';


@Component({
  selector: 'app-formation-course',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './formation-course.component.html',
  styleUrl: './formation-course.component.scss'
})
export class FormationCourseComponent
  implements OnInit, OnDestroy {

  private readonly route = inject(ActivatedRoute);

  private readonly formationService =
    inject(FormationService);

  private routeSubscription?: Subscription;

  readonly levelLabels = COURSE_LEVEL_LABELS;

  course: FormationCourseDetail | null = null;

  loading = true;

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


  // Première leçon non terminée, sinon la première.
  get nextLesson(): FormationLessonSummary | null {

    if (!this.course || this.course.lessons.length === 0) {
      return null;
    }

    return this.course.lessons.find(lesson => !lesson.completed)
      ?? this.course.lessons[0];
  }


  get startLabel(): string {

    if (!this.course || this.course.completedLessons === 0) {
      return 'Commencer la formation';
    }

    return this.course.completedLessons >= this.course.lessons.length
      ? 'Revoir la formation'
      : 'Continuer la formation';
  }


  private load(courseId: number): void {

    this.loading = true;
    this.errorMessage = '';

    if (!Number.isFinite(courseId)) {
      this.errorMessage = 'Formation introuvable.';
      this.loading = false;
      return;
    }

    this.formationService
      .getCourse(courseId)
      .subscribe({

        next: (course) => {
          this.course = course;
          this.loading = false;
        },

        error: () => {
          this.course = null;
          this.errorMessage = 'Formation introuvable.';
          this.loading = false;
        }
      });
  }
}
