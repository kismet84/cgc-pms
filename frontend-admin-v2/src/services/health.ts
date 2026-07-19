interface ActuatorHealth {
  status?: string
}

export type BackendHealth = 'checking' | 'up' | 'unavailable'

export async function probeBackendHealth(): Promise<BackendHealth> {
  try {
    const response = await fetch('/api/actuator/health', {
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) return 'unavailable'
    const payload = (await response.json()) as ActuatorHealth
    return payload.status === 'UP' ? 'up' : 'unavailable'
  } catch {
    return 'unavailable'
  }
}
