/**
 * apps/web/src/lib/a11y/use-prefers-reduced-motion.ts
 *
 * Tiny React hook returning the live value of the
 * `prefers-reduced-motion: reduce` media query. The shell CSS
 * already hard-overrides `--motion-*` tokens inside the matching
 * `@media` block (`styles/tokens.css`), so components that animate
 * via CSS get the right behaviour automatically. This hook is for
 * the few cases where JS needs to know — e.g. opting out of
 * auto-focus animation, skipping an `aria-live` update that would
 * otherwise be jarring, or guarding a programmatic `scrollIntoView`
 * that prefers `behavior: "auto"`.
 *
 * R18.4 / WCAG 2.2 SC 2.3.3 (Animation from Interactions) requires
 * that motion-heavy gestures can be disabled. The hook lets
 * components participate without re-implementing the listener.
 */
"use client";

import { useEffect, useState } from "react";

const QUERY = "(prefers-reduced-motion: reduce)";

function readInitial(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return false;
  }
  return window.matchMedia(QUERY).matches;
}

export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState<boolean>(readInitial);
  useEffect(() => {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return undefined;
    }
    const mql = window.matchMedia(QUERY);
    const onChange = (event: MediaQueryListEvent): void => {
      setReduced(event.matches);
    };
    setReduced(mql.matches);
    if (typeof mql.addEventListener === "function") {
      mql.addEventListener("change", onChange);
      return () => mql.removeEventListener("change", onChange);
    }
    mql.addListener(onChange);
    return () => mql.removeListener(onChange);
  }, []);
  return reduced;
}

export const REDUCED_MOTION_QUERY = QUERY;
