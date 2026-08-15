/**
 * apps/web/src/lib/a11y/focus-trap.ts
 *
 * E12.4 — Focus trap for modal / dialog surfaces.
 *
 * Mirrors the focusTrapStateMatrix in
 * `contracts/pwa/accessibility-policy.yaml`: IDLE → ENTERING
 * → ACTIVE → RESTORING → EXITED. The trap MUST restore focus
 * to the originating element on close (E12.4 invariant
 * `focusAlwaysVisible` + `liveRegionsOnAsyncOps`).
 */

export type FocusTrapState = "IDLE" | "ENTERING" | "ACTIVE" | "RESTORING" | "EXITED";

const TRANSITIONS: Readonly<Record<FocusTrapState, ReadonlyArray<FocusTrapState>>> = {
  IDLE: ["ENTERING"],
  ENTERING: ["ACTIVE", "EXITED"],
  ACTIVE: ["RESTORING"],
  RESTORING: ["EXITED"],
  EXITED: ["IDLE"],
};

export function canTransition(from: FocusTrapState, to: FocusTrapState): boolean {
  return TRANSITIONS[from].includes(to);
}

export function advanceFocusTrap(state: FocusTrapState, next: FocusTrapState): FocusTrapState {
  if (!canTransition(state, next)) {
    throw new Error(`invalid focus trap transition ${state} -> ${next}`);
  }
  return next;
}

export interface FocusReturnPlan {
  readonly originId: string | undefined;
  readonly restoreOnExit: boolean;
}

export function buildFocusReturnPlan(originId: string | undefined): FocusReturnPlan {
  return { originId, restoreOnExit: true };
}