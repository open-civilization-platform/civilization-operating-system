const DEFAULT_TIMEOUT_MS = 10_000;

/**
 * Opens the authenticated Nexus socket and resolves after its handshake succeeds.
 * The tokenized URL and browser-provided error details are never written to output.
 *
 * @param {string} baseUrl
 * @param {string} jwt
 * @param {typeof WebSocket} WebSocketImpl
 * @returns {Promise<void>}
 */
export function connectNexusWebSocket(baseUrl, jwt, WebSocketImpl = globalThis.WebSocket) {
  if (typeof WebSocketImpl !== 'function') {
    return Promise.reject(new Error('WebSocket is not available in this Node.js runtime'));
  }

  const url = new URL('/ws/nexus', baseUrl);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.searchParams.set('token', jwt);
  const timeoutFromEnv = Number(process.env.SMOKE_TEST_WS_TIMEOUT_MS ?? DEFAULT_TIMEOUT_MS);
  const timeoutMs = Number.isFinite(timeoutFromEnv) && timeoutFromEnv > 0
    ? timeoutFromEnv
    : DEFAULT_TIMEOUT_MS;

  return new Promise((resolve, reject) => {
    let socket;
    let settled = false;
    let timer;

    const cleanup = () => {
      clearTimeout(timer);
      socket?.removeEventListener?.('open', onOpen);
      socket?.removeEventListener?.('error', onError);
    };
    const fail = (message) => {
      if (settled) return;
      settled = true;
      cleanup();
      try { socket?.close?.(); } catch { /* best effort: do not leak URL-bearing errors */ }
      reject(new Error(message));
    };
    const onOpen = () => {
      if (settled) return;
      settled = true;
      cleanup();
      try { socket.close?.(); } catch { /* best effort after a successful handshake */ }
      resolve();
    };
    const onError = () => fail('WebSocket connection failed');

    try {
      socket = new WebSocketImpl(url.toString());
      socket.addEventListener('open', onOpen);
      socket.addEventListener('error', onError);
      timer = setTimeout(() => fail('WebSocket handshake timed out'), timeoutMs);
    } catch {
      fail('WebSocket connection failed');
    }
  });
}