import { CourseLevel } from './formation.models';


/*
 * Modèles d'édition : champs facultatifs stockés en chaîne ('' = vide)
 * pour pouvoir être liés directement aux champs du formulaire.
 */

export interface AdminQuizQuestion {
  question: string;
  options: string[];
  correctIndex: number;
  explanation: string;
}


export interface AdminLesson {
  id: number | null;
  title: string;
  content: string;
  durationMinutes: number;
  displayOrder: number;
  videoUrl: string;
  quiz: AdminQuizQuestion[];
}


export interface AdminCourse {
  id: number | null;
  title: string;
  description: string;
  level: CourseLevel;
  category: string;
  displayOrder: number;
  published: boolean;
  lessonCount: number;
  lessons: AdminLesson[];
}


/* Réponses brutes du backend. */

export interface RawAdminQuestion {
  id: number;
  question: string;
  options: string[];
  correctIndex: number;
  explanation: string | null;
}


export interface RawAdminLesson {
  id: number;
  title: string;
  content: string;
  durationMinutes: number;
  displayOrder: number;
  videoUrl: string | null;
  quiz: RawAdminQuestion[];
}


export interface RawAdminCourse {
  id: number;
  title: string;
  description: string;
  level: CourseLevel;
  category: string;
  displayOrder: number;
  published: boolean;
  lessonCount: number;
  lessons: RawAdminLesson[];
}
