// Text-to-speech via the Web Speech API where the browser supports it.
// Falls back to a no-op with a visible notice (see UI usage).

export function ttsSupported(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window;
}

export function speak(text: string, rate = 1): boolean {
  if (!ttsSupported()) return false;
  window.speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(stripMarkdown(text));
  u.rate = rate;
  u.lang = 'en-US';
  window.speechSynthesis.speak(u);
  return true;
}

export function stopSpeaking(): void {
  if (ttsSupported()) window.speechSynthesis.cancel();
}

export function stripMarkdown(text: string): string {
  return text
    .replace(/```[\s\S]*?```/g, ' code block ')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/#{1,6}\s/g, '')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
}
