import test from 'node:test';
import assert from 'node:assert/strict';
import { connectNexusWebSocket } from '../smoke-test-websocket.mjs';

class FakeWebSocket {
  static instances = [];
  listeners = new Map();

  constructor(url) {
    this.url = url;
    FakeWebSocket.instances.push(this);
  }

  addEventListener(type, listener) {
    const listeners = this.listeners.get(type) ?? [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  removeEventListener(type, listener) {
    this.listeners.set(type, (this.listeners.get(type) ?? []).filter((item) => item !== listener));
  }

  emit(type, event = {}) {
    for (const listener of this.listeners.get(type) ?? []) listener(event);
  }
}

const tick = () => new Promise((resolve) => setImmediate(resolve));

test('resolves after the authenticated Nexus socket opens without logging its token', async () => {
  FakeWebSocket.instances = [];
  const token = 'sentinel-secret-jwt';
  const captured = [];
  const originalLog = console.log;
  const originalError = console.error;
  console.log = (...args) => captured.push(args.join(' '));
  console.error = (...args) => captured.push(args.join(' '));
  try {
    const pending = connectNexusWebSocket('http://localhost:8080', token, FakeWebSocket);
    await tick();
    assert.match(FakeWebSocket.instances[0].url, /\/ws\/nexus\?token=sentinel-secret-jwt$/);
    FakeWebSocket.instances[0].emit('open');
    await pending;
    assert.equal(captured.join('\n').includes(token), false);
  } finally {
    console.log = originalLog;
    console.error = originalError;
  }
});

test('rejects a WebSocket handshake error without exposing its URL', async () => {
  FakeWebSocket.instances = [];
  const pending = connectNexusWebSocket('http://localhost:8080', 'sentinel-secret-jwt', FakeWebSocket);
  await tick();
  FakeWebSocket.instances[0].emit('error');
  await assert.rejects(pending, /WebSocket connection failed/);
});

test('rejects when the WebSocket handshake times out', async () => {
  const previous = process.env.SMOKE_TEST_WS_TIMEOUT_MS;
  process.env.SMOKE_TEST_WS_TIMEOUT_MS = '10';
  FakeWebSocket.instances = [];
  try {
    await assert.rejects(
      connectNexusWebSocket('http://localhost:8080', 'sentinel-secret-jwt', FakeWebSocket),
      /timed out/
    );
  } finally {
    if (previous === undefined) delete process.env.SMOKE_TEST_WS_TIMEOUT_MS;
    else process.env.SMOKE_TEST_WS_TIMEOUT_MS = previous;
  }
});