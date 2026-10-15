import type { ReactNode } from 'react';

export function ErrorBanner({ error }: { error: string | null }) {
  if (!error) return null;
  return <div className="alert alert-error" role="alert">{error}</div>;
}

export function SuccessBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return <div className="alert alert-success" role="status">{message}</div>;
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>;
}

export const errorMessage = (e: unknown) => (e instanceof Error ? e.message : 'Something went wrong');
