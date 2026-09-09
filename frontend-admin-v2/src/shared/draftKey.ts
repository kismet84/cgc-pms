let counter = 0

/**
 * Stable local key for an editable draft row.
 *
 * `v-for` keyed on the array index re-patches every row after a removal, so focus,
 * `aria-describedby` associations and an in-progress IME composition land on the wrong row.
 * These keys never leave the client; request payloads enumerate their fields explicitly.
 */
export function nextDraftKey(): string {
  counter += 1
  return `draft-${counter}`
}
