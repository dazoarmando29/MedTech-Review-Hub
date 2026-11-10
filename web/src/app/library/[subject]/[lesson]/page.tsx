// Server wrapper: static params for lesson pages.
import React from 'react';
import { lessons } from '@/lib/content';
import LessonView from './view';

export function generateStaticParams() {
  return lessons.map((l) => ({ subject: l.subjectId, lesson: l.id.split('/')[1] }));
}

export const dynamicParams = false;

export default function LessonPage() {
  return <LessonView />;
}
