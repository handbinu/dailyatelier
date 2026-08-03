export function chargeRequestFor(previous, amount, method, createKey) {
  if (previous?.amount === amount && previous?.method === method) return previous
  return { amount, method, key: createKey() }
}

export function invalidateChargeRequest() {
  return null
}
