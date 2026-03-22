export class ApiError extends Error {
  status: number
  body: unknown

  constructor(status: number, message: string, body: unknown) {
    super(message)
    this.status = status
    this.body = body
  }
}

function buildHeaders(init?: RequestInit) {
  const headers = new Headers(init?.headers)
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  return headers
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    credentials: 'include',
    ...init,
    headers: buildHeaders(init),
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const body = text ? tryParseJson(text) : null

  if (!response.ok) {
    const message =
      (body &&
        typeof body === 'object' &&
        'message' in body &&
        typeof body.message === 'string' &&
        body.message) ||
      `Request failed with status ${response.status}`
    throw new ApiError(response.status, message, body)
  }

  return body as T
}

function tryParseJson(text: string) {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

export function isUnauthorized(error: unknown) {
  return error instanceof ApiError && error.status === 401
}
