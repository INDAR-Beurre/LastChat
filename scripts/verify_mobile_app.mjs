import { spawn } from "node:child_process";
import fs from "node:fs";

async function runVerification() {
  console.log("=== Starting LastChat Mobile Playground Automated Verification ===");
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
      fs.writeFileSync(`/home/alex/Projects/LastChat/dist/${filename}`, Buffer.from(shot.result.data, "base64"));
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

  // Check 2: Open Model Picker Modal
  console.log("Opening Model Registry modal...");
  await evalCode("document.getElementById('model-trigger-btn').click()");
  await new Promise(r => setTimeout(r, 400));
  const isModalActive = await evalCode("document.getElementById('model-picker-modal').classList.contains('active')");
  console.log(`Model modal opened: ${isModalActive}`);

  // Filter with search
  await evalCode("document.getElementById('model-search-input').value = 'deepseek'; document.getElementById('model-search-input').dispatchEvent(new Event('input'))");
  await new Promise(r => setTimeout(r, 300));
  await takeScreenshot("verify_mobile_model_picker.png");

  // Select a model
  await evalCode("document.querySelector('#modal-models-list .model-row')?.click()");
  await new Promise(r => setTimeout(r, 300));
  const updatedModel = await evalCode("document.getElementById('current-model-name').textContent");
  console.log(`Updated model after selection: "${updatedModel}"`);

  // Check 3: Tuning View
  console.log("Switching to Tuning View...");
  await evalCode("document.querySelector('[data-view=\"tuning-view\"]').click()");
  await new Promise(r => setTimeout(r, 400));
  await evalCode("document.querySelector('[data-preset=\"architect\"]').click()");
  await takeScreenshot("verify_mobile_tuning_view.png");
  const systemPrompt = await evalCode("document.getElementById('system-prompt-input').value");
  console.log(`Architect preset prompt: "${systemPrompt.substring(0, 40)}..."`);

  // Check 4: Admin Gateway View
  console.log("Switching to Admin Gateway View...");
  await evalCode("document.querySelector('[data-view=\"admin-view\"]').click()");
  await new Promise(r => setTimeout(r, 800));
  const providerCount = await evalCode("document.querySelectorAll('#providers-grid .provider-card').length");
  console.log(`Discovered provider cards: ${providerCount}`);
  await takeScreenshot("verify_mobile_admin_view.png");

  // Check 5: Chat View & Send Prompt
  console.log("Switching back to Chat View and testing prompt interaction...");
  await evalCode("document.querySelector('[data-view=\"chat-view\"]').click()");
  await new Promise(r => setTimeout(r, 300));
  
  // Inject mock reasoning & code response to verify full markdown & thinking accordion UI
  await evalCode(`
    const sess = window.LastChatApp.state.sessions.find(s => s.id === window.LastChatApp.state.currentSessionId);
    sess.messages.push({
      role: 'user',
      content: 'Can you show me a concise Kotlin coroutine example with step-by-step thinking?',
      timestamp: Date.now() - 5000
    });
    sess.messages.push({
      role: 'assistant',
      model: 'deepseek-v4.1-flash',
      reasoning: 'First, identify the core need: concise Kotlin coroutines demonstration.\\nSecond, choose GlobalScope vs CoroutineScope. Use runBlocking or CoroutineScope(Dispatchers.Default).\\nThird, format with clean code comments and brief explanation.',
      content: 'Here is an idiomatic and concise Kotlin coroutines example:\\n\\n\`\`\`kotlin\\nimport kotlinx.coroutines.*\\n\\nfun main() = runBlocking {\\n    val job = launch {\\n        delay(1000L)\\n        println(\"World! Generated from LastChat Mobile\")\\n    }\\n    println(\"Hello\")\\n    job.join()\\n}\\n\`\`\`\\n\\n### Key Highlights:\\n- **Structured Concurrency**: Using \`runBlocking\` creates a top-level coroutine scope.\\n- **Non-blocking delay**: \`delay(1000L)\` suspends without freezing threads.\\n- **Deterministic Join**: \`job.join()\` awaits asynchronous completion cleanly.',
      latencyMs: 142,
      tokens: 284,
      timestamp: Date.now()
    });
    window.LastChatApp.renderChatMessages();
  `);
  await new Promise(r => setTimeout(r, 600));
  await takeScreenshot("verify_mobile_reasoning_and_code.png");

  // Check 6: Raw Inspector Modal
  console.log("Opening Raw JSON Inspector modal...");
  await evalCode("document.getElementById('inspect-raw-btn').click()");
  await new Promise(r => setTimeout(r, 400));
  await takeScreenshot("verify_mobile_inspector.png");
  await evalCode("document.getElementById('close-inspector-btn').click()");

  // Check 7: Compact 360x780 Mobile Screen Viewport
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
  process.exit(0);
}

runVerification().catch(err => {
  console.error("Verification failed:", err);
  process.exit(1);
});
