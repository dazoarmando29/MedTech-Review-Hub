// Server wrapper: provides static params for export.
import React from 'react';
import { subjects } from '@/lib/content';
import SubjectView from './view';

export function generateStaticParams() {
  return subjects.map((s) => ({ subject: s.id }));
}

export const dynamicParams = false;

export default function SubjectPage() {
  return <SubjectView />;
}
