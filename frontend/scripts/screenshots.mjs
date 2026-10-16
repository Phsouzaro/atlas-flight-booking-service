// Tira as capturas de tela do README a partir do app rodando no Compose (http://localhost:4200).
// Usa o Edge (ou Chrome) já instalado em modo headless, controlado pelo protocolo DevTools com o WebSocket
// nativo do Node: nada para baixar. O chat fala com a OpenAI de verdade (duas mensagens, centavos).
//
// Uso (na pasta frontend, com o Compose de pé):
//   SMOKE_USER_EMAIL=... SMOKE_USER_PASSWORD=... node scripts/screenshots.mjs
// Opcional: BROWSER=caminho/do/navegador  APP_URL=http://localhost:4200  JAEGER_URL=http://localhost:16686
import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const APP = process.env.APP_URL ?? 'http://localhost:4200';
const JAEGER = process.env.JAEGER_URL ?? 'http://localhost:16686';
const EMAIL = process.env.SMOKE_USER_EMAIL;
const PASSWORD = process.env.SMOKE_USER_PASSWORD;
const OUT = new URL('../../docs/images/', import.meta.url);
const PORT = 9333;

if (!EMAIL || !PASSWORD) {
  console.error('Defina SMOKE_USER_EMAIL e SMOKE_USER_PASSWORD (estão no .env da raiz).');
  process.exit(1);
}

const BROWSERS = [
  process.env.BROWSER,
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
].filter(Boolean);
const browserPath = BROWSERS.find((p) => existsSync(p));
if (!browserPath) {
  console.error('Nenhum Edge ou Chrome encontrado; passe o caminho em BROWSER.');
  process.exit(1);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const inDays = (days) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);

// ---- DevTools ----------------------------------------------------------------------------------

const profile = await mkdtemp(join(tmpdir(), 'atlas-shots-'));
const browser = spawn(browserPath, [
  '--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--no-first-run', '--no-default-browser-check', '--hide-scrollbars', 'about:blank',
], { stdio: 'ignore' });

let target;
for (let i = 0; i < 50 && !target; i++) {
  try {
    target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page');
  } catch {
    await sleep(200);
  }
}
const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve) => ws.addEventListener('open', resolve, { once: true }));
let nextId = 0;
const pending = new Map();
const consoleErrors = [];
ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data);
  if (msg.method === 'Runtime.exceptionThrown') {
    consoleErrors.push(msg.params.exceptionDetails.exception?.description ?? msg.params.exceptionDetails.text);
  }
  if (msg.method === 'Runtime.consoleAPICalled' && msg.params.type === 'error') {
    consoleErrors.push(msg.params.args.map((a) => a.value ?? a.description).join(' '));
  }
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  }
});
const cdp = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++nextId;
  pending.set(id, (msg) => (msg.error ? reject(new Error(`${method}: ${msg.error.message}`)) : resolve(msg.result)));
  ws.send(JSON.stringify({ id, method, params }));
});
const run = async (expression) =>
  (await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.value;

async function viewport(width, height, scheme) {
  await cdp('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: width < 600 });
  await cdp('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-color-scheme', value: scheme }] });
}

async function go(url, settleMs = 1500) {
  await cdp('Page.navigate', { url });
  await sleep(settleMs);
}

async function waitFor(expression, timeoutMs = 60_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (await run(expression)) {
      return;
    }
    await sleep(500);
  }
  throw new Error(`timeout esperando: ${expression}`);
}

async function shot(name) {
  await sleep(400);
  const { data } = await cdp('Page.captureScreenshot', { format: 'png' });
  await writeFile(new URL(`${name}.png`, OUT), Buffer.from(data, 'base64'));
  console.log(`docs/images/${name}.png`);
}

/** Digita no campo do chat como o usuário faria (o componente ouve o evento input) e envia. */
async function say(text) {
  const before = await run("document.querySelectorAll('.message:not(.user)').length");
  await run(`(() => { const t = document.querySelector('textarea'); t.value = ${JSON.stringify(text)};
    t.dispatchEvent(new Event('input')); document.querySelector('form').dispatchEvent(new Event('submit')); })()`);
  await waitFor(`document.querySelectorAll('.message:not(.user)').length > ${before} && !document.querySelector('.typing')`);
}

// ---- roteiro -----------------------------------------------------------------------------------

try {
  await mkdir(OUT, { recursive: true });
  await cdp('Page.enable');
  await cdp('Runtime.enable');

  await viewport(1280, 800, 'light');
  await go(`${APP}/login`);
  await shot('login');

  await run(`(() => {
    const set = (sel, v) => { const el = document.querySelector(sel); el.value = v; el.dispatchEvent(new Event('input')); };
    set('input[type=email]', ${JSON.stringify(EMAIL)}); set('input[type=password]', ${JSON.stringify(PASSWORD)}); })()`);
  // o botão só habilita na próxima renderização do Angular; clicar antes é clicar em botão desabilitado
  await waitFor("!document.querySelector('button[type=submit]').disabled", 5_000);
  await run("document.querySelector('button[type=submit]').click()");
  try {
    await waitFor("location.pathname === '/chat'", 15_000);
  } catch (e) {
    const state = await run(`(async () => ({ path: location.pathname,
      alert: document.querySelector('[role=alert]')?.textContent,
      emailField: document.querySelector('input[type=email]')?.value,
      requests: performance.getEntriesByType('resource').map(e => e.name.replace(location.origin, '')).filter(n => n.startsWith('/api')),
      api: (await fetch('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: ${JSON.stringify(EMAIL)}, password: ${JSON.stringify(PASSWORD)} }) })).status }))()`);
    throw new Error(`login não concluiu: ${JSON.stringify(state)} console: ${consoleErrors.join(' | ').slice(0, 800)}`);
  }

  // conversa nova, busca com cards de voo
  await run("[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Nova conversa').click()");
  await say(`Quais voos tem de GRU para o GIG no dia ${inDays(12)}? Só eu.`);
  await shot('chat-busca');

  // pedido de cancelamento: vira proposta com card de confirmação, nada é executado
  await run("[...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'Nova conversa').click()");
  await say('Quero cancelar a minha reserva confirmada mais antiga. Quanto eu recebo?');
  await shot('chat-acao-pendente');

  await viewport(1280, 800, 'dark');
  await shot('chat-escuro');

  await viewport(1280, 800, 'light');
  await go(`${APP}/reservas`, 2500);
  await shot('reservas');

  await viewport(390, 844, 'light');
  await go(`${APP}/chat`, 2500);
  await shot('celular');

  // trace do último turno do chat no Jaeger
  await viewport(1280, 800, 'light');
  // Jaeger 2: busca em /api/v3, no formato OTLP (resourceSpans > scopeSpans > spans)
  const now = new Date();
  const query = new URLSearchParams({
    'query.service_name': 'atlas-flight-booking-service',
    'query.search_depth': '50',
    'query.start_time_min': new Date(now.getTime() - 3_600_000).toISOString(),
    'query.start_time_max': now.toISOString(),
  });
  const found = await (await fetch(`${JAEGER}/api/v3/traces?${query}`)).json();
  const spans = (found.result?.resourceSpans ?? []).flatMap((r) => r.scopeSpans.flatMap((s) => s.spans));
  const turn = spans.filter((s) => s.name.startsWith('chat '))
    .sort((a, b) => Number(BigInt(b.startTimeUnixNano) - BigInt(a.startTimeUnixNano)))[0];
  if (turn) {
    await go(`${JAEGER}/trace/${turn.traceId}`, 3000);
    await shot('jaeger-trace');
  } else {
    console.warn('Nenhum trace de chat no Jaeger; pulando a captura.');
  }
} finally {
  ws.close();
  browser.kill();
}
