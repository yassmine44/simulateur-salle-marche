import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import {
  COURSE_LEVEL_LABELS,
  CourseLevel,
  FormationCourse
} from '../../models/formation.models';

import { FormationService }
  from '../../services/formation.service';


type LevelFilter = 'ALL' | CourseLevel;


@Component({
  selector: 'app-formation',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink
  ],
  templateUrl: './formation.component.html',
  styleUrl: './formation.component.scss'
})
export class FormationComponent implements OnInit {

  private readonly formationService =
    inject(FormationService);

  readonly levelLabels = COURSE_LEVEL_LABELS;

  readonly filters: { key: LevelFilter; label: string }[] = [
    { key: 'ALL', label: 'Toutes' },
    { key: 'BEGINNER', label: COURSE_LEVEL_LABELS.BEGINNER },
    { key: 'INTERMEDIATE', label: COURSE_LEVEL_LABELS.INTERMEDIATE },
    { key: 'ADVANCED', label: COURSE_LEVEL_LABELS.ADVANCED }
  ];

  courses: FormationCourse[] = [];

  selectedFilter: LevelFilter = 'ALL';

  loading = true;

  errorMessage = '';


  ngOnInit(): void {

    this.formationService
      .getCourses()
      .subscribe({

        next: (courses) => {
          this.courses = courses;
          this.loading = false;
        },

        error: () => {
          this.errorMessage =
            'Impossible de charger les formations. Réessayez plus tard.';
          this.loading = false;
        }
      });
  }


  get filteredCourses(): FormationCourse[] {

    if (this.selectedFilter === 'ALL') {
      return this.courses;
    }

    return this.courses.filter(
      course => course.level === this.selectedFilter
    );
  }


  get totalLessons(): number {

    return this.courses.reduce(
      (sum, course) => sum + course.lessonCount,
      0
    );
  }


  get completedLessons(): number {

    return this.courses.reduce(
      (sum, course) => sum + course.completedLessons,
      0
    );
  }


  get overallPercent(): number {

    return this.totalLessons === 0
      ? 0
      : Math.round(
        this.completedLessons * 100 / this.totalLessons
      );
  }


  selectFilter(filter: LevelFilter): void {
    this.selectedFilter = filter;
  }


  actionLabel(course: FormationCourse): string {

    if (course.completedLessons === 0) {
      return 'Commencer';
    }

    return course.completedLessons >= course.lessonCount
      ? 'Revoir'
      : 'Continuer';
  }
}
