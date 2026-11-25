import { describe, it, expect } from 'vitest'
import { AuthRequiredError, checkAuthResponse } from './authCheck'
import { jsonResponse, textResponse } from '../test/gatewayMock'

describe('checkAuthResponse', () => {
  it('throws AuthRequiredError when the gateway answers with its HTML login page', () => {
    const res = textResponse('<html>login</html>', 200, 'text/html; charset=utf-8')
    expect(() => checkAuthResponse(res)).toThrow(AuthRequiredError)
    expect(() => checkAuthResponse(res)).toThrow(
      'Authentication required. Please log in to the Ignition Gateway first.'
    )
  })

  it('accepts a JSON response', () => {
    expect(() => checkAuthResponse(jsonResponse({ success: true }))).not.toThrow()
  })
})
