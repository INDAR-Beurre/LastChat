import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const PROJECT_DIR = path.resolve(import.meta.dirname, "..");
const DIST_DIR = path.join(PROJECT_DIR, "dist");
const SERVER_PORT = 8765;
const CDP_PORT = 9338;

async function runVerification() {
  console.log("=== Starting LastLab Automated Mobile App Verification ===");

  // 1. Launch Preview Server for full static + API support
  const server = spawn("node", [
    path.join(PROJECT_DIR, "scripts/preview_server.mjs"),
    "--port",
    String(SERVER_PORT)
  ], { stdio: "inherit" });

  await new Promise(r => setTimeout(r, 1200));

  // 2. Launch Helium Headless Browser in Mobile Viewport
  const browser = spawn("/opt/helium-browser-bin/helium", [
    "--headless=new",
    "--no-sandbox",
    `--remote-debugging-port=${CDP_PORT}`,
    "--ozone-platform=headless",
    "--window-size=390,844",
    "about:blank"
  ]);

  let wsUrl = null;
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/version`);
      if (res.ok) {
        const data = await res.json();
        wsUrl = data.webSocketDebuggerUrl;
        break;
      }
    } catch {}
    await new Promise(r => setTimeout(r, 150));
  }

  if (!wsUrl) {
    console.error("Failed to connect to Helium browser CDP on port " + CDP_PORT);
    browser.kill();
    server.kill();
    process.exit(1);
  }

  const ws = new WebSocket(wsUrl);
  let id = 1;
  const pending = new Map();
  const consoleLogs = [];

  ws.onmessage = (event) => {
    const msg = JSON.parse(event.data);
    if (msg.method === "Runtime.consoleAPICalled") {
      const text = msg.params.args.map(a => a.value || a.description).join(" ");
      consoleLogs.push(`[Browser] ${text}`);
    }
    if (msg.id && pending.has(msg.id)) {
      pending.get(msg.id)(msg);
      pending.delete(msg.id);
    }
  };

  await new Promise((resolve) => ws.onopen = resolve);

  function send(method, params = {}, sessionId = undefined) {
    return new Promise((resolve) => {
      const reqId = id++;
      pending.set(reqId, resolve);
      ws.send(JSON.stringify({ id: reqId, method, params, sessionId }));
    });
  }

  const target = await send("Target.createTarget", { url: "about:blank" });
  const targetId = target.result.targetId;
  const attached = await send("Target.attachToTarget", { targetId, flatten: true });
  const sessionId = attached.result.sessionId;

  await send("Page.enable", {}, sessionId);
  await send("Runtime.enable", {}, sessionId);
  await send("Emulation.setDeviceMetricsOverride", {
    width: 390,
    height: 844,
    deviceScaleFactor: 2,
    mobile: true
  }, sessionId);

  async function evalCode(expr) {
    const res = await send("Runtime.evaluate", { expression: expr, returnByValue: true }, sessionId);
    return res.result?.result?.value;
  }

  async function takeScreenshot(filename) {
    await evalCode("document.querySelectorAll('.toast, .toast-container').forEach(t => t.remove())");
    const shot = await send("Page.captureScreenshot", { format: "png" }, sessionId);
    if (shot.result?.data) {
      fs.writeFileSync(path.join(DIST_DIR, filename), Buffer.from(shot.result.data, "base64"));
      console.log(`Saved screenshot: dist/${filename}`);
    }
  }

  console.log(`Navigating to http://127.0.0.1:${SERVER_PORT}/mobile/index.html...`);
  await send("Page.navigate", { url: `http://127.0.0.1:${SERVER_PORT}/mobile/index.html` }, sessionId);
  await new Promise(r => setTimeout(r, 2000));

  // Check 1: Top bar, branding and initial model loading
  const modelName = await evalCode("document.getElementById('current-model-name')?.textContent || 'Model Trigger'");
  console.log(`Initial model loaded: "${modelName}"`);
  await takeScreenshot("verify_mobile_chat_empty.png");

  // Check 2: Open Admin Model Matrix Popover
  console.log("Opening Admin Model Registry Popover...");
  await evalCode("document.getElementById('model-trigger-btn')?.click()");
  await new Promise(r => setTimeout(r, 600));
  const isModalActive = await evalCode(`(() => {
    const el = document.getElementById('model-picker-modal');
    return !!(el && (el.getAttribute('data-state') === 'open' || el.offsetParent !== null));
  })()`);
  console.log(`Admin Model popover opened: ${isModalActive}`);

  // Check Admin Quick Shelf chips
  const quickShelfChips = await evalCode("document.querySelectorAll('#model-quick-shelf .shelf-chip').length");
  console.log(`Admin quick shelf chips: ${quickShelfChips}`);

  // Filter with search
  console.log("Filtering models by search 'deepseek'...");
  await evalCode(`(() => {
    const input = document.getElementById('model-search-input');
    if (input) {
      input.value = 'deepseek';
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
  })()`);
  await new Promise(r => setTimeout(r, 400));
  await takeScreenshot("verify_mobile_model_picker.png");

  // Select a model
  await evalCode("document.querySelector('#modal-models-list .model-row')?.click()");
  await new Promise(r => setTimeout(r, 400));
  const updatedModel = await evalCode("document.getElementById('current-model-name')?.textContent");
  console.log(`Updated model after selection: "${updatedModel}"`);

  // Check 3: Android Back Button Bridge
  console.log("Testing Android Back Button Bridge...");
  await evalCode("document.getElementById('model-trigger-btn')?.click()");
  await new Promise(r => setTimeout(r, 400));
  const backHandled = await evalCode("(window.LastLabApp || window.LastChatApp)?.onBackPressed()");
  console.log(`Back button closed popover: ${backHandled}`);

  // Check 4: Send conversation prompt and test streaming reasoning + markdown
  console.log("Testing prompt execution and streaming reasoning...");
  await evalCode(`(() => {
    const textarea = document.querySelector('textarea');
    if (textarea) {
      const nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, "value")?.set;
      if (nativeSetter) {
        nativeSetter.call(textarea, 'Can you show me a concise Kotlin coroutine example with step-by-step thinking?');
      } else {
        textarea.value = 'Can you show me a concise Kotlin coroutine example with step-by-step thinking?';
      }
      textarea.dispatchEvent(new Event('input', { bubbles: true }));
      textarea.dispatchEvent(new Event('change', { bubbles: true }));
    }
  })()`);
  await new Promise(r => setTimeout(r, 400));
  
  // Click send button
  await evalCode(`(() => {
    const sendBtn = document.getElementById('send-message-btn') || document.querySelector('button[type="submit"]');
    if (sendBtn && !sendBtn.disabled) sendBtn.click();
    else {
      // Fallback: dispatch Enter key on textarea
      const textarea = document.querySelector('textarea');
      if (textarea) textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    }
  })()`);

  console.log("Waiting for streaming reasoning and response...");
  let streamStarted = false;
  for (let i = 0; i < 25; i++) {
    await new Promise(r => setTimeout(r, 200));
    streamStarted = await evalCode(`!!document.querySelector('[data-message-role="assistant"]')`);
    if (streamStarted) break;
  }
  console.log(`Stream started: ${streamStarted}`);

  for (let i = 0; i < 40; i++) {
    await new Promise(r => setTimeout(r, 200));
    const generating = await evalCode(`(() => {
      const stopBtn = document.querySelector('button#send-message-btn.bg-destructive');
      const isLoading = document.querySelector('[data-message-loading="true"]');
      return !!(stopBtn || isLoading);
    })()`);
    if (!generating && streamStarted) {
      console.log(`Stream generation completed in ~${(i + 1) * 200}ms`);
      break;
    }
  }

  // Align reasoning trace and code block into view with top bar padding offset
  await evalCode(`(() => {
    const reasoning = document.querySelector('[data-part="reasoning"]') || document.querySelector('[data-message-role="assistant"]');
    if (reasoning) {
      reasoning.scrollIntoView({ behavior: 'instant', block: 'start' });
      const log = document.querySelector('[role="log"]');
      if (log) log.scrollTop = Math.max(0, log.scrollTop - 54);
    }
  })()`);
  await new Promise(r => setTimeout(r, 500));
  await takeScreenshot("verify_mobile_reasoning_and_code.png");

  // Check 5: Compact 360x780 Mobile Screen Viewport
  console.log("Testing compact 360x780 mobile viewport...");
  await send("Emulation.setDeviceMetricsOverride", {
    width: 360,
    height: 780,
    deviceScaleFactor: 2,
    mobile: true
  }, sessionId);
  await new Promise(r => setTimeout(r, 500));
  await evalCode(`(() => {
    const log = document.querySelector('[role="log"]');
    if (log) {
      log.scrollTop = log.scrollHeight;
    }
  })()`);
  await new Promise(r => setTimeout(r, 400));
  await takeScreenshot("verify_mobile_compact_360.png");

  // Check 6: Hyperparameter Tuning Bottom Sheet
  console.log("Testing Hyperparameter Tuning Bottom Sheet...");
  await evalCode("window.dispatchEvent(new CustomEvent('lastlab:open-tuning'))");
  await new Promise(r => setTimeout(r, 500));
  const tuningActive = await evalCode("!!document.getElementById('playground-tuning-drawer')");
  console.log(`Tuning sheet opened: ${tuningActive}`);
  await evalCode("(window.LastLabApp || window.LastChatApp)?.onBackPressed() || document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))");
  await new Promise(r => setTimeout(r, 400));

  // Check 7: Raw Protocol & Telemetry Inspector Dialog
  console.log("Testing Raw Protocol & Telemetry Inspector Dialog...");
  await evalCode("document.getElementById('inspect-raw-btn')?.click() || window.dispatchEvent(new CustomEvent('lastlab:open-inspector'))");
  await new Promise(r => setTimeout(r, 500));
  const inspectorActive = await evalCode("!!document.getElementById('raw-inspector-modal')");
  console.log(`Raw Inspector dialog opened: ${inspectorActive}`);
  if (!inspectorActive) throw new Error("Failed to open Raw Inspector dialog!");
  await evalCode("document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))");
  await new Promise(r => setTimeout(r, 400));

  console.log("=== Mobile App Verification Completed Successfully! ===");
  browser.kill();
  server.kill();
  process.exit(0);
}

runVerification().catch(err => {
  console.error("Verification failed:", err);
  process.exit(1);
});
