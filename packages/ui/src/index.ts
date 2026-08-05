/**
 * `@genealogy/ui` — design system primitives.
 *
 * E1.5 ships the minimum primitives the PWA shell needs (Button,
 * Card) without pulling in the full shadcn/ui Radix dependency
 * tree. The primitives follow the shadcn/ui variant API so future
 * migrations are drop-in; we just swap the underlying styling
 * primitives.
 *
 * Why no `@radix-ui/*` dependency? E1.5 is the platform foundation
 * and the Next.js bundle budget (§10.3) is enforced per page.
 * Adding Radix now would force a heavier bundle on every page
 * even though only the authenticated editor needs its dropdown /
 * dialog primitives. E6 picks up the Radix versions of the same
 * primitives as part of the editor milestone.
 */
export { Button } from "./button";
export type { ButtonProps, ButtonVariant, ButtonSize } from "./button";
export { Card, CardHeader, CardTitle, CardDescription, CardContent, CardFooter } from "./card";
export type { CardProps } from "./card";
