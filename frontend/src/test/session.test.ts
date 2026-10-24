import { beforeEach, expect, it, vi } from 'vitest'

beforeEach(() => { sessionStorage.clear(); vi.resetModules(); vi.unstubAllGlobals() })

it('connectsOnceAndCachesTokenForBrowserSession', async () => {
  const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify({ token: 'jwt', clientId: 'owner', expiresIn: 3600 })))
  vi.stubGlobal('fetch', fetcher)
  const { ensureSession } = await import('../auth/session')
  expect(await Promise.all([ensureSession(), ensureSession()])).toEqual([{ token: 'jwt', clientId: 'owner' }, { token: 'jwt', clientId: 'owner' }])
  vi.resetModules()
  const reloaded = await import('../auth/session')
  expect(await reloaded.ensureSession()).toEqual({ token: 'jwt', clientId: 'owner' })
  expect(fetcher).toHaveBeenCalledTimes(1)
  expect(fetcher).toHaveBeenCalledWith('/api/v1/auth/connect', { method: 'POST' })
})
