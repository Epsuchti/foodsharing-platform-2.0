import { LearningChapter, LearningChapterGroup } from '../../api';

/** A group heading of the knowledge area with its chapters and their position in reading order (C1). */
export interface ChapterGroup {
  id: LearningChapterGroup;
  chapters: { chapter: LearningChapter; position: number }[];
}

const ORDER: readonly LearningChapterGroup[] = [
  LearningChapterGroup.Basics,
  LearningChapterGroup.More,
  LearningChapterGroup.StoreManager,
  LearningChapterGroup.Ambassador,
];

/** Chapters (already in reading order) under their groups in group order; chapters without a group are BASICS. */
export function groupChapters(list: readonly LearningChapter[]): ChapterGroup[] {
  const groups = new Map<LearningChapterGroup, ChapterGroup>();
  list.forEach((chapter, index) => {
    const id = chapter.group ?? LearningChapterGroup.Basics;
    const group = groups.get(id) ?? { id, chapters: [] };
    group.chapters.push({ chapter, position: index + 1 });
    groups.set(id, group);
  });
  return ORDER.filter((id) => groups.has(id)).map((id) => groups.get(id)!);
}
