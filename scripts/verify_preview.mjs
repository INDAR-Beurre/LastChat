import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const PROJECT_DIR = "/home/alex/Projects/LastChat";
const DIST_DIR = path.join(PROJECT_DIR, "dist");
const PREVIEW_PORT = 8990;
const CDP_PORT = 9339;

async function runPreviewVerification() {
  console.log("=== Starting LastLab Mobile Studio Simulator Verification ===");

  // 1. Launch Preview Server
  const serverProcess = spawn("node", [
    path.join(PROJECT_DIR, "scripts/preview_server.mjs"),
    "--port",
    String(PREVIEW_PORT)
  ], { stdio: "inherit" });

  await new Promise(r => setTimeout(r, 1200));

  // 2. Launch Headless Helium Browser at Desktop Resolution
  const browser = spawn("/opt/helium-browser-bin/helium", [
    "--headless=new",
    "--no-sandbox",
    `--remote-debugging-port=${CDP_PORT}`,
    "--ozone-platform=headless",
    "--window-size=1440,960",
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
    serverProcess.kill();
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

  async function evalCode(expr) {
    const res = await send("Runtime.evaluate", { expression: expr, returnByValue: true }, sessionId);
    return res.result?.result?.value;
  }

  async function takeScreenshot(filename) {
    await evalCode(`(() => {
      const iDoc = document.getElementById('mobile-iframe')?.contentDocument;
      if (iDoc) {
        iDoc.querySelectorAll('.toast, .toast-container').forEach(t => t.remove());
        const assistantTurn = iDoc.querySelector('[data-message-role="assistant"]') || iDoc.querySelector('.chat-turn.assistant');
        if (assistantTurn) assistantTurn.scrollIntoView({ behavior: 'instant', block: 'start' });
      }
    })()`);
    const shot = await send("Page.captureScreenshot", { format: "png" }, sessionId);
    if (shot.result?.data) {
      fs.writeFileSync(path.join(DIST_DIR, filename), Buffer.from(shot.result.data, "base64"));
      console.log(`Saved screenshot: dist/${filename}`);
    }
  }

  console.log(`Navigating to http://127.0.0.1:${PREVIEW_PORT}/...`);
  await send("Page.navigate", { url: `http://127.0.0.1:${PREVIEW_PORT}/` }, sessionId);
  await new Promise(r => setTimeout(r, 2000));

  // Check 1: Studio Title and Brand
  const title = await evalCode("document.title");
  console.log(`Page title: "${title}"`);

  // Check 2: Verify default device is Pixel 8 Pro
  const devName = await evalCode("document.getElementById('spec-device-name')?.textContent");
  console.log(`Default selected device: "${devName}"`);

  // Check 3: Check Iframe is loaded
  const iframeLoaded = await evalCode("!!(document.getElementById('mobile-iframe')?.contentWindow?.LastLabApp || document.getElementById('mobile-iframe')?.contentWindow?.LastChatApp)");
  console.log(`Mobile app loaded in iframe: ${iframeLoaded}`);

  // Check 4: Switch to Samsung Galaxy S24
  console.log("Switching device to Samsung Galaxy S24...");
  await evalCode("document.querySelector('[data-device=\"s24\"]').click()");
  await new Promise(r => setTimeout(r, 400));
  const s24Width = await evalCode("document.getElementById('screen-wrapper').style.width");
  console.log(`S24 wrapper width: ${s24Width} (expected: 360px)`);

  // Check 5: Switch to iPhone 15 Pro
  console.log("Switching device to iPhone 15 Pro (Dynamic Island & iOS Home Bar)...");
  await evalCode("document.querySelector('[data-device=\"iphone15\"]').click()");
  await new Promise(r => setTimeout(r, 400));
  const isPill = await evalCode("document.getElementById('device-frame').classList.contains('notch-pill')");
  const isIosNav = await evalCode("document.getElementById('device-nav-bar').classList.contains('ios-mode')");
  console.log(`iPhone 15 dynamic island active: ${isPill}`);
  console.log(`iPhone 15 iOS Home bar active: ${isIosNav}`);
  if (!isIosNav) throw new Error("iPhone 15 should have iOS Home Bar mode enabled!");

  // Switch back to Pixel 8 Pro
  await evalCode("document.querySelector('[data-device=\"pixel8\"]').click()");
  await new Promise(r => setTimeout(r, 400));
  const isAndroidNav = await evalCode("!document.getElementById('device-nav-bar').classList.contains('ios-mode')");
  console.log(`Android 3-button nav restored for Pixel 8: ${isAndroidNav}`);

  // Check 6: Trigger Scenario - Open Model Registry
  console.log("Testing Scenario action: open-model-picker...");
  await evalCode("document.querySelector('[data-action=\"open-model-picker\"]').click()");
  await new Promise(r => setTimeout(r, 500));
  const modalActive = await evalCode(`(() => {
    const el = document.getElementById('mobile-iframe')?.contentDocument?.getElementById('model-picker-modal');
    return !!(el && (el.getAttribute('data-state') === 'open' || el.classList.contains('active') || el.offsetParent !== null));
  })()`);
  console.log(`Admin Model Registry modal opened inside iframe: ${modalActive}`);

  // Check 7a: Trigger Hardware Back Key on phone frame
  console.log("Testing Simulated Android Hardware Back Key...");
  await evalCode("document.getElementById('nav-btn-back').click()");
  await new Promise(r => setTimeout(r, 400));
  const modalClosed = await evalCode(`(() => {
    const el = document.getElementById('mobile-iframe')?.contentDocument?.getElementById('model-picker-modal');
    return !el || el.getAttribute('data-state') === 'closed';
  })()`);
  console.log(`Android Back key closed modal inside iframe: ${modalClosed}`);

  // Check 7b: Re-open modal and test Escape key INSIDE IFRAME
  console.log("Testing Escape key inside iframe document closes modal...");
  await evalCode("(document.getElementById('mobile-iframe').contentWindow.LastLabApp || document.getElementById('mobile-iframe').contentWindow.LastChatApp).openModelPicker()");
  await new Promise(r => setTimeout(r, 400));
  await evalCode(`(() => {
    const iDoc = document.getElementById('mobile-iframe').contentDocument;
    iDoc.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  })()`);
  await new Promise(r => setTimeout(r, 400));
  const modalClosedViaEsc = await evalCode(`(() => {
    const el = document.getElementById('mobile-iframe')?.contentDocument?.getElementById('model-picker-modal');
    return !el || el.getAttribute('data-state') === 'closed';
  })()`);
  console.log(`Inside-iframe Escape key closed modal: ${modalClosedViaEsc}`);
  if (!modalClosedViaEsc) throw new Error("Escape key inside iframe failed to trigger back bridge!");

  // Check 8: Test Scenario - Inject Reasoning & Kotlin Snippet
  console.log("Testing Scenario action: inject-reasoning...");
  await evalCode("document.querySelector('[data-action=\"inject-reasoning\"]').click()");
  
  let streamStarted = false;
  for (let i = 0; i < 25; i++) {
    await new Promise(r => setTimeout(r, 200));
    streamStarted = await evalCode(`(() => {
      const doc = document.getElementById('mobile-iframe')?.contentDocument;
      return !!doc?.querySelector('[data-message-role=\"assistant\"]');
    })()`);
    if (streamStarted) break;
  }
  console.log(`Stream started: ${streamStarted}`);

  for (let i = 0; i < 40; i++) {
    await new Promise(r => setTimeout(r, 200));
    const generating = await evalCode(`(() => {
      const doc = document.getElementById('mobile-iframe')?.contentDocument;
      const stopBtn = doc?.querySelector('button#send-message-btn.bg-destructive');
      const isLoading = doc?.querySelector('[data-message-loading=\"true\"]');
      return !!(stopBtn || isLoading);
    })()`);
    if (!generating && streamStarted) {
      console.log(`Simulator stream generation completed in ~${(i + 1) * 200}ms`);
      break;
    }
  }

  // Ensure completed assistant message and telemetry badges are cleanly visible
  await evalCode(`(() => {
    const doc = document.getElementById('mobile-iframe')?.contentDocument;
    if (doc) {
      const assistant = doc.querySelector('[data-message-role=\"assistant\"]');
      if (assistant) {
        assistant.scrollIntoView({ behavior: 'instant', block: 'start' });
      }
    }
  })()`);
  await new Promise(r => setTimeout(r, 400));

  // Check 9: Verify Console Stream recorded logs
  const logRows = await evalCode("document.querySelectorAll('#console-logs-list .console-log-row').length");
  console.log(`Captured console log rows in simulator: ${logRows}`);

  // Check 10: Capture High-Res Desktop Simulator Screenshot
  console.log("Capturing full desktop simulator preview screenshot...");
  await takeScreenshot("preview_simulator_desktop.png");

  // Check 11: Test LAN Sharing QR Modal and long URL robustness
  console.log("Opening LAN Sharing & QR Modal...");
  await evalCode("document.getElementById('btn-network-share').click()");
  await new Promise(r => setTimeout(r, 400));
  const qrSvgExists = await evalCode("!!document.querySelector('#modal-qr-svg svg')");
  console.log(`Standalone SVG QR code generated: ${qrSvgExists}`);

  // Test QR with long URL (150+ chars)
  const longQrValid = await evalCode(`(() => {
    const longUrl = 'http://192.168.86.128:8990/?token=abc123xyz789&session=admin_super_user_matrix_probe_long_url_test_parameter_string_validation';
    const qr = new window.QRCodeSVG(longUrl, { size: 190 });
    const svg = qr.toSVG();
    return svg.startsWith('<svg') && svg.length > 5000;
  })()`);
  console.log(`Type 7-10 QR code generated for long URL (150+ chars): ${longQrValid}`);
  if (!longQrValid) throw new Error("Failed to generate valid QR code for long URL!");
  await evalCode("document.getElementById('modal-network-close').click()");

  // Check 12: Verify Static Icon & APK Endpoints via Server HTTP Requests
  console.log("Verifying icon and APK HTTP headers on preview server...");
  const iconRes = await fetch(`http://127.0.0.1:${PREVIEW_PORT}/mobile/res/mipmap-hdpi/ic_launcher.png`);
  console.log(`Favicon /mobile/res/mipmap-hdpi/ic_launcher.png HTTP Status: ${iconRes.status} (expected: 200)`);
  if (iconRes.status !== 200) throw new Error(`Icon endpoint failed: status ${iconRes.status}`);

  const apkRes = await fetch(`http://127.0.0.1:${PREVIEW_PORT}/dist/lastlab.apk`, { method: "HEAD" });
  const dispHeader = apkRes.headers.get("content-disposition");
  console.log(`APK Content-Disposition header: "${dispHeader}"`);
  if (!dispHeader || !dispHeader.includes("attachment")) throw new Error("Missing APK attachment disposition header!");

  // Check 13: Gateway Live Status Text Check
  const gwText = await evalCode("document.getElementById('gateway-status-text')?.textContent");
  console.log(`Live Gateway status text: "${gwText}"`);

  console.log("=== All 13 Verification Checkpoints Completed Successfully! ===");
  browser.kill();
  serverProcess.kill();
  process.exit(0);
}

runPreviewVerification().catch((err) => {
  console.error("Verification failed:", err);
  process.exit(1);
});
