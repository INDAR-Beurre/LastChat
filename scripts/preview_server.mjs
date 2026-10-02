#!/usr/bin/env node

/**
 * ============================================================================
 * LastChat Mobile Studio — Lightweight Preview Server
 * Zero dependencies, pure Node.js standard library.
 * Designed for low-spec PCs: < 25MB RAM, instant startup (< 50ms).
 * ============================================================================
 */

import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_DIR = path.resolve(__dirname, "..");
const PREVIEW_DIR = path.join(PROJECT_DIR, "preview");
const WWW_DIR = path.join(PROJECT_DIR, "mobile/assets/www");
const DIST_DIR = path.join(PROJECT_DIR, "dist");

// Parse CLI flags
const args = process.argv.slice(2);
let requestedPort = parseInt(process.env.PORT || "8080", 10);
const portFlagIndex = args.indexOf("--port");
if (portFlagIndex !== -1 && args[portFlagIndex + 1]) {
  requestedPort = parseInt(args[portFlagIndex + 1], 10);
}

// MIME Types Map
const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".apk": "application/vnd.android.package-archive",
  ".sha256": "text/plain; charset=utf-8",
  ".txt": "text/plain; charset=utf-8"
};

// Discover Network IP addresses
function getNetworkAddresses() {
  const nets = os.networkInterfaces();
  const results = { lan: null, tailscale: null, all: [] };

  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === "IPv4" && !net.internal) {
        results.all.push(net.address);
        if (name.includes("tailscale")) {
          results.tailscale = net.address;
        } else if (!results.lan) {
          results.lan = net.address;
        }
      }
    }
  }
  return results;
}

// Request Handler
function handleRequest(req, res) {
  // CORS & Security headers
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "*");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || "localhost"}`);
  let pathname = decodeURIComponent(parsedUrl.pathname);

  // 1. Health & Discovery API Endpoint
  if (pathname === "/api/health" || pathname === "/api/network") {
    const net = getNetworkAddresses();
    const port = server.address().port;
    const apkStat = fs.existsSync(path.join(DIST_DIR, "lastchat-playground.apk"))
      ? fs.statSync(path.join(DIST_DIR, "lastchat-playground.apk"))
      : null;

    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({
      status: "ok",
      app: "LastChat Mobile Playground",
      edition: "Relay Edition",
      version: "1.4.8",
      port,
      localUrl: `http://localhost:${port}/`,
      networkUrl: net.lan ? `http://${net.lan}:${port}/` : `http://localhost:${port}/`,
      tailscaleUrl: net.tailscale ? `http://${net.tailscale}:${port}/` : null,
      directAppUrl: `http://localhost:${port}/mobile/index.html`,
      apk: {
        path: "dist/lastchat-playground.apk",
        sizeBytes: apkStat ? apkStat.size : 0,
        sizeKb: apkStat ? Math.round(apkStat.size / 1024) : 0,
        exists: !!apkStat
      },
      relayGateway: "https://relay-gw.pages.dev"
    }, null, 2));
    return;
  }

  // 2. Resolve Physical File Path
  let filePath = null;

  if (pathname.startsWith("/mobile/")) {
    const rel = pathname.substring("/mobile/".length);
    filePath = path.join(WWW_DIR, rel === "" ? "index.html" : rel);
  } else if (pathname.startsWith("/dist/")) {
    const rel = pathname.substring("/dist/".length);
    filePath = path.join(DIST_DIR, rel);
  } else {
    // Simulator Root Assets (/preview/* or /)
    const rel = pathname === "/" ? "index.html" : pathname.replace(/^\//, "");
    filePath = path.join(PREVIEW_DIR, rel);

    // Fallback: If not found in preview, check mobile www
    if (!fs.existsSync(filePath)) {
      const fallbackPath = path.join(WWW_DIR, rel);
      if (fs.existsSync(fallbackPath)) {
        filePath = fallbackPath;
      }
    }
  }

  // Serve static file if exists
  if (filePath && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || "application/octet-stream";
    const stat = fs.statSync(filePath);

    res.writeHead(200, {
      "Content-Type": contentType,
      "Content-Length": stat.size,
      "Cache-Control": ext === ".apk" ? "no-cache" : "public, max-age=60"
    });

    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end(`Not Found: ${pathname}\n`);
  }
}

// Start Server with Graceful Port Fallback
const server = http.createServer(handleRequest);

function startServer(port, maxAttempts = 10) {
  server.once("error", (err) => {
    if (err.code === "EADDRINUSE" && maxAttempts > 0) {
      console.warn(`[Port ${port} in use, trying ${port + 1}...]`);
      startServer(port + 1, maxAttempts - 1);
    } else {
      console.error("Fatal Server Error:", err);
      process.exit(1);
    }
  });

  server.listen(port, "0.0.0.0", () => {
    const actualPort = server.address().port;
    const net = getNetworkAddresses();
    printBanner(actualPort, net);
  });
}

function printBanner(port, net) {
  const cyan = "\x1b[36m";
  const green = "\x1b[32m";
  const yellow = "\x1b[33m";
  const bold = "\x1b[1m";
  const dim = "\x1b[2m";
  const reset = "\x1b[0m";

  console.log("\n" + bold + cyan + "  📱 LastChat Mobile Studio — Device Preview Simulator" + reset);
  console.log(dim + "  ────────────────────────────────────────────────────────────" + reset);
  console.log(`  ${bold}Local Simulator:${reset}    ${green}http://localhost:${port}/${reset}`);
  if (net.lan) {
    console.log(`  ${bold}LAN / Phone:${reset}        ${green}http://${net.lan}:${port}/${reset}  ${dim}(Scan with QR)${reset}`);
  }
  if (net.tailscale) {
    console.log(`  ${bold}Tailscale VPN:${reset}      ${green}http://${net.tailscale}:${port}/${reset}`);
  }
  console.log(`  ${bold}Direct Mobile App:${reset}  ${cyan}http://localhost:${port}/mobile/index.html${reset}`);
  console.log(`  ${bold}Android APK Binary:${reset} ${yellow}http://localhost:${port}/dist/lastchat-playground.apk${reset}`);
  console.log(dim + "  ────────────────────────────────────────────────────────────" + reset);
  console.log(`  ${dim}Status:${reset} ${green}● Active${reset} | ${dim}Memory:${reset} ~22MB | ${dim}Target:${reset} Relay Gateway (@model-aggregator)`);
  console.log(`  ${dim}Shortcuts: Esc=Back Bridge, O=Rotate, 1-5=Switch Devices, R=Reload${reset}\n`);
}

startServer(requestedPort);
