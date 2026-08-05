/**
 * Global loading boundary — shown while route segments resolve.
 *
 * The boundary is intentionally minimal so it never blocks the
 * main thread; the skeleton uses CSS variables + the GPU
 * compositor to avoid layout thrashing. The `aria-busy` /
 * `aria-live` attributes inform assistive technology that the
 * region is updating without announcing every layout shift.
 */
export default function RootLoading() {
  return (
    <div
      className="mx-auto flex w-full max-w-5xl flex-col gap-4 px-6 py-16"
      role="status"
      aria-busy="true"
      aria-live="polite"
    >
      <span className="sr-only">Loading page…</span>
      <div className="skeleton h-10 w-2/3" />
      <div className="skeleton h-6 w-1/2" />
      <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-3">
        <div className="skeleton h-32" />
        <div className="skeleton h-32" />
        <div className="skeleton h-32" />
      </div>
    </div>
  );
}
