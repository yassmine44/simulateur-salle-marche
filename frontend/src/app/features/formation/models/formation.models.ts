export type CourseLevel =
  | 'BEGINNER'
  | 'INTERMEDIATE'
  | 'ADVANCED';

export const COURSE_LEVEL_LABELS: Record<CourseLevel, string> = {
  BEGINNER: 'Débutant',
  INTERMEDIATE: 'Intermédiaire',
  ADVANCED: 'Avancé'
};


export interface FormationCourse {
  id: number;
  title: string;
  description: string;
  level: CourseLevel;
  category: string;
  lessonCount: number;
  completedLessons: number;
  totalMinutes: number;
  progressPercent: number;
}


export interface FormationLessonSummary {
  id: number;
  title: string;
  durationMinutes: number;
  hasQuiz: boolean;
  completed: boolean;
}


export interface FormationCourseDetail {
  id: number;
  title: string;
  description: string;
  level: CourseLevel;
  category: string;
  completedLessons: number;
  totalMinutes: number;
  progressPercent: number;
  lessons: FormationLessonSummary[];
}


export interface FormationQuizQuestion {
  id: number;
  question: string;
  options: string[];
}


export interface FormationLesson {
  id: number;
  courseId: number;
  courseTitle: string;
  title: string;
  content: string;
  durationMinutes: number;
  videoUrl: string | null;
  completed: boolean;
  bestScorePercent: number | null;
  previousLessonId: number | null;
  nextLessonId: number | null;
  quiz: FormationQuizQuestion[];
}


export interface QuizQuestionResult {
  questionId: number;
  correct: boolean;
  correctIndex: number;
  explanation: string | null;
}


export interface QuizResult {
  correctCount: number;
  totalQuestions: number;
  scorePercent: number;
  passed: boolean;
  lessonCompleted: boolean;
  results: QuizQuestionResult[];
}


export interface MessageResponse {
  message: string;
}


/*
 * Contenu d'une leçon découpé en blocs affichables.
 * Aucun HTML n'est injecté : le texte est rendu via l'interpolation Angular.
 */
export type LessonBlock =
  | { type: 'heading'; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; items: string[] };


export function parseLessonContent(content: string): LessonBlock[] {

  const blocks: LessonBlock[] = [];

  let paragraph: string[] = [];
  let list: string[] = [];

  const flushParagraph = (): void => {
    if (paragraph.length > 0) {
      blocks.push({ type: 'paragraph', text: paragraph.join(' ') });
      paragraph = [];
    }
  };

  const flushList = (): void => {
    if (list.length > 0) {
      blocks.push({ type: 'list', items: list });
      list = [];
    }
  };

  for (const rawLine of content.split(/\r?\n/)) {

    const line = rawLine.trim();

    if (line === '') {
      flushParagraph();
      flushList();
      continue;
    }

    if (line.startsWith('## ')) {
      flushParagraph();
      flushList();
      blocks.push({ type: 'heading', text: line.substring(3).trim() });
      continue;
    }

    if (line.startsWith('- ')) {
      flushParagraph();
      list.push(line.substring(2).trim());
      continue;
    }

    flushList();
    paragraph.push(line);
  }

  flushParagraph();
  flushList();

  return blocks;
}



/*
 * Extrait l'identifiant d'une vidéo YouTube (11 caractères) depuis
 * watch?v=ID, youtu.be/ID ou embed/ID. Renvoie null si le lien n'est pas valide.
 * Même règle que le backend (AdminFormationService).
 */
export function youtubeVideoId(
  url: string | null | undefined
): string | null {

  if (!url) {
    return null;
  }

  const match = url.trim().match(
    /^https:\/\/(?:www\.)?(?:youtube\.com\/(?:watch\?v=|embed\/)|youtu\.be\/)([A-Za-z0-9_-]{11})(?:[&?#].*)?$/
  );

  return match ? match[1] : null;
}
