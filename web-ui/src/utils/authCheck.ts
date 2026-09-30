/**
 * Auth check utilities for detecting Gateway authentication failures.
 *
 * When Ignition returns UNAUTHORIZED, it redirects to /web/login and
 * returns HTML instead of JSON. These utilities detect that condition
 * and provide a clear error message.
 */

export class AuthRequiredError extends Error {
  constructor() {
    super('Authentication required. Please log in to the Ignition Gateway first.')
    this.name = 'AuthRequiredError'
  }
}

/**
 * Check if a fetch response indicates an auth failure (HTML instead of JSON).
 * Throws AuthRequiredError if the response is HTML (login redirect).
 */
export function checkAuthResponse(response: Response): void {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('text/html')) {
    throw new AuthRequiredError()
  }
}
