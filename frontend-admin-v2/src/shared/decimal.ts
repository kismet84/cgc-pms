/**
 * Integer-cent arithmetic for the money paths.
 *
 * Amounts travel as decimal strings end to end (backend `BigDecimal`), and 18-digit contract
 * amounts exceed `Number.MAX_SAFE_INTEGER`, so summing or subtracting them through `Number`
 * silently loses cents. Everything here works on `bigint` cents and rounds half-up at 2 decimals,
 * matching the backend's `RoundingMode.HALF_UP`.
 */

const DECIMAL_PATTERN = /^(-?)(\d+)(?:\.(\d+))?$/

export function parseCents(value: string | number | null | undefined): bigint {
  const normalized = value == null ? '' : String(value).trim()
  if (!normalized) return 0n
  const match = DECIMAL_PATTERN.exec(normalized)
  if (!match) return 0n
  const [, sign, integer, fraction = ''] = match
  const cents = BigInt(`${integer}${fraction.padEnd(2, '0').slice(0, 2)}`)
  const rounded = (fraction[2] ?? '0') >= '5' ? cents + 1n : cents
  return sign === '-' ? -rounded : rounded
}

export function sumCents(values: readonly (string | number | null | undefined)[]): bigint {
  return values.reduce<bigint>((total, value) => total + parseCents(value), 0n)
}

export function centsToDecimalString(cents: bigint): string {
  const negative = cents < 0n
  const absolute = negative ? -cents : cents
  const integer = absolute / 100n
  const fraction = String(absolute % 100n).padStart(2, '0')
  return `${negative ? '-' : ''}${integer}.${fraction}`
}
