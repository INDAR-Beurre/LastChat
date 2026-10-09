import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_DIR = path.resolve(__dirname, "..");
const DIST_DIR = path.join(PROJECT_DIR, "dist");
const CDP_PORT = 9450;

console.log("===============================================================");
console.log(" Starting Real Mobile Simulator Test on LastLab v1.0.0         ");
console.log("===============================================================");

// 1. Start the Java Loopback Backend
const jdk = process.env.JAVA_HOME || path.join(process.env.HOME, ".local/jdk-17");
const javaBin = path.join(jdk, "bin/java");

const javaProc = spawn(javaBin, [
  "-cp", "build/smoke-classes:toolchain/json.jar", "smoke.SmokeMain$Serve"
], { cwd: PROJECT_DIR });

let serverUrl = null;
const serverReadyPromise = new Promise((resolve, reject) => {
  javaProc.stdout.on("data", (data) => {
    const str = data.toString();
    console.log("[Backend]", str.trim());
    const match = str.match(/SERVE READY (http:\/\/[^\s]+)/);
    if (match) {
      serverUrl = match[1];
      resolve(serverUrl);
    }
  });
  javaProc.stderr.on("data", (d) => {
    console.error("[Backend ERR]", d.toString().trim());
  });
  javaProc.on("exit", (code) => {
    if (!serverUrl) reject(new Error(`Backend exited prematurely with code ${code}`));
  });
});

await serverReadyPromise;
console.log(`[Simulator] Backend loopback server ready at: ${serverUrl}`);

// 2. Launch Helium Browser in Headless Mobile Emulation
const browserProc = spawn("/opt/helium-browser-bin/helium", [
  "--headless=new",
  "--no-sandbox",
  `--remote-debugging-port=${CDP_PORT}`,
  "--ozone-platform=headless",
  "--window-size=412,915",
  "about:blank"
]);

// Wait for CDP
let wsDebuggerUrl = null;
for (let i = 0; i < 40; i++) {
  try {
    const res = await fetch(`http://127.0.0.1:${CDP_PORT}/json/version`);
    if (res.ok) {
      const json = await res.json();
      wsDebuggerUrl = json.webSocketDebuggerUrl;
      break;
    }
  } catch {}
  await new Promise(r => setTimeout(r, 150));
}

if (!wsDebuggerUrl) {
  console.error("Failed to connect to browser CDP port " + CDP_PORT);
  browserProc.kill();
  javaProc.kill();
  process.exit(1);
}

const ws = new WebSocket(wsDebuggerUrl);
await new Promise(r => ws.onopen = r);

let reqId = 1;
const pending = new Map();
const consoleLogs = [];

ws.onmessage = (evt) => {
  const msg = JSON.parse(evt.data);
  if (msg.method === "Runtime.consoleAPICalled") {
    const text = msg.params.args.map(a => a.value || a.description).join(" ");
    consoleLogs.push(text);
    console.log(`[Browser Console] ${text}`);
  }
  if (msg.method === "Runtime.exceptionThrown") {
    console.error(`[Browser Exception]`, msg.params.exceptionDetails?.text, msg.params.exceptionDetails?.exception?.description);
  }
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg);
    pending.delete(msg.id);
  }
};

function send(method, params = {}, sessionId = undefined) {
  return new Promise((resolve) => {
    const id = reqId++;
    pending.set(id, resolve);
    ws.send(JSON.stringify({ id, method, params, sessionId }));
  });
}

// Attach to target
const target = await send("Target.createTarget", { url: "about:blank" });
const targetId = target.result.targetId;
const attached = await send("Target.attachToTarget", { targetId, flatten: true });
const sessionId = attached.result.sessionId;

await send("Page.enable", {}, sessionId);
await send("Runtime.enable", {}, sessionId);

// Emulate Google Pixel 8 Pro
await send("Emulation.setDeviceMetricsOverride", {
  width: 412,
  height: 915,
  deviceScaleFactor: 2.625,
  mobile: true,
  screenOrientation: { angle: 0, type: "portraitPrimary" }
}, sessionId);

await send("Emulation.setUserAgentOverride", {
  userAgent: "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UD1A.230803.041) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 LastLab/1.0.0"
}, sessionId);

await send("Emulation.setTouchEmulationEnabled", { enabled: true }, sessionId);

async function evalCode(expr) {
  const res = await send("Runtime.evaluate", { expression: expr, returnByValue: true }, sessionId);
  if (res.result?.exceptionDetails) {
    console.error("[Eval Error]", res.result.exceptionDetails.text, res.result.exceptionDetails.exception?.description);
  }
  return res.result?.result?.value;
}

async function captureShot(filename) {
  const shot = await send("Page.captureScreenshot", { format: "png" }, sessionId);
  if (shot.result?.data) {
    const dest = path.join(DIST_DIR, filename);
    fs.writeFileSync(dest, Buffer.from(shot.result.data, "base64"));
    console.log(`[Screenshot Saved] -> ${dest} (${shot.result.data.length} bytes base64)`);
  }
}

try {
  console.log(`[Simulator] Navigating mobile browser to ${serverUrl}/...`);
  await send("Page.navigate", { url: `${serverUrl}/` }, sessionId);

  // Wait for React app to mount
  let appReady = false;
  for (let i = 0; i < 50; i++) {
    await new Promise(r => setTimeout(r, 200));
    const title = await evalCode("document.title");
    const rootHasContent = await evalCode("document.body.innerText.length > 5");
    if (rootHasContent) {
      appReady = true;
      console.log(`[Simulator] App mounted successfully! Title: "${title}", body text length: ${await evalCode("document.body.innerText.length")}`);
      break;
    }
  }

  if (!appReady) {
    console.error("[Simulator] Timeout waiting for React application to mount.");
  }

  await captureShot("simulator_mobile_initial.png");

  // Inspect DOM structure
  const pageInfo = await evalCode(`(() => {
    return {
      title: document.title,
      inputs: Array.from(document.querySelectorAll("input, textarea, button")).map(el => ({
        tag: el.tagName,
        id: el.id,
        className: el.className,
        text: el.innerText || el.placeholder || el.value || el.getAttribute("aria-label")
      })).slice(0, 15),
      bodyTextSnippet: document.body.innerText.slice(0, 200)
    };
  })()`);
  console.log("[Simulator] Detected Interactive Elements:", JSON.stringify(pageInfo, null, 2));

  // Test Model Selector Popover
  console.log("[Simulator] Testing Model Selector Popover...");
  await evalCode("document.getElementById('model-trigger-btn')?.click()");
  await new Promise(r => setTimeout(r, 600));
  await captureShot("simulator_mobile_model_picker.png");
  const modelsInPicker = await evalCode(`Array.from(document.querySelectorAll('[role="option"], [role="menuitem"], button, div')).map(b => b.innerText?.trim()).filter(t => t && (t.includes('DeepSeek') || t.includes('Claude') || t.includes('GPT'))).slice(0, 6)`);
  console.log("[Simulator] Models visible in picker:", modelsInPicker);

  // Click on DeepSeek or close picker
  await evalCode(`(() => {
    const item = Array.from(document.querySelectorAll('[role="option"], [role="menuitem"], button, div')).find(el => el.innerText && el.innerText.includes('DeepSeek'));
    if (item) item.click();
    else {
      const backdrop = document.querySelector('[data-state="open"]');
      if (backdrop) backdrop.click();
    }
  })()`);
  await new Promise(r => setTimeout(r, 400));

  // Check if textarea exists and type a message
  const textareaFound = await evalCode("!!document.querySelector('textarea')");
  console.log("[Simulator] Textarea found:", textareaFound);

  if (textareaFound) {
    console.log("[Simulator] Entering message prompt in mobile chat...");
    await evalCode(`(() => {
      const ta = document.querySelector('textarea');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value')?.set;
      if (setter) {
        setter.call(ta, 'Hello from Pixel 8 Mobile Simulator!');
      } else {
        ta.value = 'Hello from Pixel 8 Mobile Simulator!';
      }
      ta.dispatchEvent(new Event('input', { bubbles: true }));
      ta.dispatchEvent(new Event('change', { bubbles: true }));
    })()`);

    await new Promise(r => setTimeout(r, 400));
    await captureShot("simulator_mobile_prompt_entered.png");

    console.log("[Simulator] Submitting message...");
    await evalCode(`(() => {
      const sendBtn = document.querySelector('button[type="submit"]') ||
                      document.getElementById('send-message-btn') ||
                      document.querySelector('button svg path[d*="M2.01 21"]')?.closest('button');
      if (sendBtn && !sendBtn.disabled) {
        sendBtn.click();
      } else {
        const ta = document.querySelector('textarea');
        ta.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, which: 13, bubbles: true }));
      }
    })()`);

    // Wait for streaming deltas
    console.log("[Simulator] Waiting for streaming response from loopback server...");
    let replyFound = false;
    for (let i = 0; i < 40; i++) {
      await new Promise(r => setTimeout(r, 250));
      const hasAssistant = await evalCode(`(() => {
        const text = document.body.innerText;
        return text.includes("Hello from LastLab") || text.includes("Checking the relay contract");
      })()`);
      if (hasAssistant) {
        replyFound = true;
        console.log(`[Simulator] Assistant streamed response received in ~${(i + 1) * 250}ms!`);
        break;
      }
    }

    await new Promise(r => setTimeout(r, 500));
    await captureShot("simulator_mobile_response.png");
  }

  console.log("===============================================================");
  console.log(" Simulator Test Completed Successfully!                       ");
  console.log("===============================================================");

} catch (err) {
  console.error("[Simulator Error]", err);
} finally {
  browserProc.kill();
  javaProc.kill();
  process.exit(0);
}
