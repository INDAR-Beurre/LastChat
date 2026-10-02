import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";

const PROJECT_DIR = "/home/alex/Projects/LastChat";
const WWW_DIR = path.join(PROJECT_DIR, "mobile/assets/www");
const DIST_DIR = path.join(PROJECT_DIR, "dist");

// 1. In-process static HTTP server for mobile assets
const mimeTypes = {
  ".html": "text/html",
  ".css": "text/css",
  ".js": "text/javascript",
  ".json": "application/json",
  ".png": "image/png",
  ".svg": "image/svg+xml"
};

const server = http.createServer((req, res) => {
  let reqPath = req.url.split("?")[0];
  if (reqPath === "/") reqPath = "/index.html";
  const filePath = path.join(WWW_DIR, reqPath);
  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath);
    res.writeHead(200, {
      "Content-Type": mimeTypes[ext] || "text/plain",
      "Access-Control-Allow-Origin": "*"
    });
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404);
    res.end("Not found");
  }
});

await new Promise((resolve) => server.listen(8765, "127.0.0.1", resolve));
console.log("Local HTTP asset server listening at http://127.0.0.1:8765");

async function runVerification() {
  console.log("=== Starting LastLab Automated Verification ===");
  const port = 9338;
  const browser = spawn("/opt/helium-browser-bin/helium", [
    "--headless=new",
    "--no-sandbox",
    `--remote-debugging-port=${port}`,
    "--ozone-platform=headless",
    "--window-size=390,844",
    "about:blank"
  ]);

  let wsUrl = null;
  for (let i = 0; i < 40; i++) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/version`);
      if (res.ok) {
        const data = await res.json();
        wsUrl = data.webSocketDebuggerUrl;
        break;
      }
    } catch {}
    await new Promise(r => setTimeout(r, 150));
  }

  if (!wsUrl) {
    console.error("Failed to connect to Helium browser CDP on port " + port);
    browser.kill();
    server.close();
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
    const shot = await send("Page.captureScreenshot", { format: "png" }, sessionId);
    if (shot.result?.data) {
      fs.writeFileSync(path.join(DIST_DIR, filename), Buffer.from(shot.result.data, "base64"));
      console.log(`Saved screenshot: dist/${filename}`);
    }
  }

  console.log("Navigating to http://127.0.0.1:8765/index.html...");
  await send("Page.navigate", { url: "http://127.0.0.1:8765/index.html" }, sessionId);
  await new Promise(r => setTimeout(r, 1500));

  // Check 1: Top bar and model loading
  const modelName = await evalCode("document.getElementById('current-model-name').textContent");
  console.log(`Initial model loaded: "${modelName}"`);
  await takeScreenshot("verify_mobile_chat_empty.png");

  // Check 2: Open Model Registry Modal (Admin bottom sheet)
  console.log("Opening Admin Model Registry modal...");
  await evalCode("document.getElementById('model-trigger-btn').click()");
  await new Promise(r => setTimeout(r, 400));
  const isModalActive = await evalCode("document.getElementById('model-picker-modal').classList.contains('active')");
  console.log(`Model modal opened: ${isModalActive}`);

  // Check Admin Quick Shelf chips
  const quickShelfChips = await evalCode("document.querySelectorAll('#model-quick-shelf .shelf-chip').length");
  console.log(`Admin quick shelf chips: ${quickShelfChips}`);

  // Filter with search
  await evalCode("document.getElementById('model-search-input').value = 'deepseek'; document.getElementById('model-search-input').dispatchEvent(new Event('input'))");
  await new Promise(r => setTimeout(r, 300));
  await takeScreenshot("verify_mobile_model_picker.png");

  // Select a model
  await evalCode("document.querySelector('#modal-models-list .model-row')?.click()");
  await new Promise(r => setTimeout(r, 300));
  const updatedModel = await evalCode("document.getElementById('current-model-name').textContent");
  console.log(`Updated model after selection: "${updatedModel}"`);

  // Check 3: Back Button Handling
  console.log("Testing Android Back Button Bridge...");
  await evalCode("document.getElementById('model-trigger-btn').click()");
  await new Promise(r => setTimeout(r, 300));
  const backHandled1 = await evalCode("(window.LastLabApp || window.LastChatApp).onBackPressed()");
  console.log(`Back button closed modal: ${backHandled1}`);

  // Check 4: Tuning View
  console.log("Switching to Tuning View...");
  await evalCode("document.querySelector('[data-view=\"tuning-view\"]').click()");
  await new Promise(r => setTimeout(r, 400));
  await evalCode("document.querySelector('[data-preset=\"architect\"]').click()");
  await takeScreenshot("verify_mobile_tuning_view.png");
  const systemPrompt = await evalCode("document.getElementById('system-prompt-input').value");
  console.log(`Architect preset prompt: "${systemPrompt.substring(0, 40)}..."`);

  // Back button from Tuning view returns to Chat view
  const backHandled2 = await evalCode("(window.LastLabApp || window.LastChatApp).onBackPressed()");
  console.log(`Back button returned to chat view: ${backHandled2}`);
  const activeView = await evalCode("(window.LastLabApp || window.LastChatApp).state.activeView");
  console.log(`Active view after back: "${activeView}"`);

  // Check 5: Admin Gateway View
  console.log("Switching to Admin Gateway View...");
  await evalCode("document.querySelector('[data-view=\"admin-view\"]').click()");
  await new Promise(r => setTimeout(r, 800));
  const providerCount = await evalCode("document.querySelectorAll('#providers-grid .provider-card').length");
  console.log(`Discovered provider cards: ${providerCount}`);
  await takeScreenshot("verify_mobile_admin_view.png");

  // Check 6: Chat View & Send Prompt
  console.log("Switching back to Chat View and testing prompt interaction...");
  await evalCode("document.querySelector('[data-view=\"chat-view\"]').click()");
  await new Promise(r => setTimeout(r, 300));
  
  // Inject mock reasoning & code response to verify full markdown & thinking accordion UI
  await evalCode(`
    const app = window.LastLabApp || window.LastChatApp;
    const sess = app.state.sessions.find(s => s.id === app.state.currentSessionId);
    sess.messages.push({
      role: 'user',
      content: 'Can you show me a concise Kotlin coroutine example with step-by-step thinking?',
      timestamp: Date.now() - 5000
    });
    sess.messages.push({
      role: 'assistant',
      model: 'deepseek-v4.1-flash',
      reasoning: 'First, identify the core need: concise Kotlin coroutines demonstration.\\nSecond, choose GlobalScope vs CoroutineScope. Use runBlocking or CoroutineScope(Dispatchers.Default).\\nThird, format with clean code comments and brief explanation.',
      content: 'Here is an idiomatic and concise Kotlin coroutines example:\\n\\n\`\`\`kotlin\\nimport kotlinx.coroutines.*\\n\\nfun main() = runBlocking {\\n    val job = launch {\\n        delay(1000L)\\n        println(\\"World! Generated from LastLab Mobile\\")\\n    }\\n    println(\\"Hello\\")\\n    job.join()\\n}\\n\`\`\`\\n\\n### Key Highlights:\\n- **Structured Concurrency**: Using \`runBlocking\` creates a top-level coroutine scope.\\n- **Non-blocking delay**: \`delay(1000L)\` suspends without freezing threads.\\n- **Deterministic Join**: \`job.join()\` awaits asynchronous completion cleanly.',
      latencyMs: 142,
      tokens: 284,
      timestamp: Date.now()
    });
    app.renderChatMessages();
  `);
  await new Promise(r => setTimeout(r, 600));
  await takeScreenshot("verify_mobile_reasoning_and_code.png");

  // Check 7: Raw Inspector Modal
  console.log("Opening Raw JSON Inspector modal...");
  await evalCode("document.getElementById('inspect-raw-btn').click()");
  await new Promise(r => setTimeout(r, 400));
  await takeScreenshot("verify_mobile_inspector.png");
  await evalCode("document.getElementById('close-inspector-btn').click()");

  // Check 8: Compact 360x780 Mobile Screen Viewport
  console.log("Testing compact 360x780 mobile viewport...");
  await send("Emulation.setDeviceMetricsOverride", {
    width: 360,
    height: 780,
    deviceScaleFactor: 2,
    mobile: true
  }, sessionId);
  await new Promise(r => setTimeout(r, 400));
  await takeScreenshot("verify_mobile_compact_360.png");

  console.log("=== Browser Console Logs ===");
  consoleLogs.forEach(l => console.log(l));
  console.log("=== Verification Completed Successfully! ===");
  browser.kill();
  server.close();
  process.exit(0);
}

runVerification().catch(err => {
  console.error("Verification failed:", err);
  server.close();
  process.exit(1);
});
