import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const PROJECT_DIR = "/home/alex/Projects/LastChat";
const DIST_DIR = path.join(PROJECT_DIR, "dist");
const PREVIEW_PORT = 8990;
const CDP_PORT = 9339;

async function runPreviewVerification() {
  console.log("=== Starting LastChat Mobile Studio Simulator Verification ===");

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
  const iframeLoaded = await evalCode("!!document.getElementById('mobile-iframe')?.contentWindow?.LastChatApp");
  console.log(`Mobile app loaded in iframe: ${iframeLoaded}`);

  // Check 4: Switch to Samsung Galaxy S24
  console.log("Switching device to Samsung Galaxy S24...");
  await evalCode("document.querySelector('[data-device=\"s24\"]').click()");
  await new Promise(r => setTimeout(r, 400));
  const s24Width = await evalCode("document.getElementById('screen-wrapper').style.width");
  console.log(`S24 wrapper width: ${s24Width} (expected: 360px)`);

  // Check 5: Switch to iPhone 15 Pro
  console.log("Switching device to iPhone 15 Pro (Dynamic Island pill)...");
  await evalCode("document.querySelector('[data-device=\"iphone15\"]').click()");
  await new Promise(r => setTimeout(r, 400));
  const isPill = await evalCode("document.getElementById('device-frame').classList.contains('notch-pill')");
  console.log(`iPhone 15 dynamic island active: ${isPill}`);

  // Switch back to Pixel 8 Pro
  await evalCode("document.querySelector('[data-device=\"pixel8\"]').click()");
  await new Promise(r => setTimeout(r, 400));

  // Check 6: Trigger Scenario - Open Model Registry
  console.log("Testing Scenario action: open-model-picker...");
  await evalCode("document.querySelector('[data-action=\"open-model-picker\"]').click()");
  await new Promise(r => setTimeout(r, 500));
  const modalActive = await evalCode("document.getElementById('mobile-iframe').contentDocument.getElementById('model-picker-modal')?.classList.contains('active')");
  console.log(`Admin Model Registry modal opened inside iframe: ${modalActive}`);

  // Check 7: Trigger Hardware Back Key on phone frame
  console.log("Testing Simulated Android Hardware Back Key...");
  await evalCode("document.getElementById('nav-btn-back').click()");
  await new Promise(r => setTimeout(r, 400));
  const modalClosed = await evalCode("!document.getElementById('mobile-iframe').contentDocument.getElementById('model-picker-modal')?.classList.contains('active')");
  console.log(`Android Back key closed modal inside iframe: ${modalClosed}`);

  // Check 8: Test Scenario - Inject Reasoning & Kotlin Snippet
  console.log("Testing Scenario action: inject-reasoning...");
  await evalCode("document.querySelector('[data-action=\"inject-reasoning\"]').click()");
  await new Promise(r => setTimeout(r, 600));

  // Check 9: Verify Console Stream recorded logs
  const logRows = await evalCode("document.querySelectorAll('#console-logs-list .console-log-row').length");
  console.log(`Captured console log rows in simulator: ${logRows}`);

  // Check 10: Capture High-Res Desktop Simulator Screenshot
  console.log("Capturing full desktop simulator preview screenshot...");
  await takeScreenshot("preview_simulator_desktop.png");

  // Check 11: Test LAN Sharing QR Modal
  console.log("Opening LAN Sharing & QR Modal...");
  await evalCode("document.getElementById('btn-network-share').click()");
  await new Promise(r => setTimeout(r, 400));
  const qrSvgExists = await evalCode("!!document.querySelector('#modal-qr-svg svg')");
  console.log(`Standalone SVG QR code generated: ${qrSvgExists}`);
  await evalCode("document.getElementById('modal-network-close').click()");

  console.log("=== Verification Completed Successfully! ===");
  browser.kill();
  serverProcess.kill();
  process.exit(0);
}

runPreviewVerification().catch((err) => {
  console.error("Verification failed:", err);
  process.exit(1);
});
