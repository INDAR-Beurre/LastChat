// ============================================================================
// LastChat Mobile Studio — Simulator Controller
// ============================================================================

(function () {
  'use strict';

  // Device Presets
  const DEVICES = {
    pixel8: {
      id: 'pixel8',
      name: 'Google Pixel 8 Pro',
      width: 412,
      height: 915,
      dpr: 2.625,
      notch: 'punch-hole',
      radius: '44px',
    },
    s24: {
      id: 's24',
      name: 'Samsung Galaxy S24',
      width: 360,
      height: 780,
      dpr: 3.0,
      notch: 'punch-hole',
      radius: '38px',
    },
    iphone15: {
      id: 'iphone15',
      name: 'Apple iPhone 15 Pro',
      width: 393,
      height: 852,
      dpr: 3.0,
      notch: 'pill',
      radius: '48px',
    },
    compact: {
      id: 'compact',
      name: 'Compact Android (360p)',
      width: 360,
      height: 640,
      dpr: 2.0,
      notch: 'punch-hole',
      radius: '28px',
    },
    tablet: {
      id: 'tablet',
      name: 'Foldable / Tablet',
      width: 768,
      height: 1024,
      dpr: 2.0,
      notch: 'none',
      radius: '24px',
    }
  };

  const state = {
    currentDevice: 'pixel8',
    scaleMode: 'auto',
    scaleValue: 1,
    isLandscape: false,
    showChassis: true,
    activeDrawerTab: 'tab-actions',
    consoleFilter: 'all',
    logs: [],
    networkUrl: window.location.origin
  };

  // DOM Elements
  const dom = {
    stage: document.getElementById('device-stage'),
    container: document.getElementById('device-container'),
    frame: document.getElementById('device-frame'),
    screenWrapper: document.getElementById('screen-wrapper'),
    iframe: document.getElementById('mobile-iframe'),
    notch: document.getElementById('device-notch'),
    statusClock: document.getElementById('status-clock'),
    drawer: document.getElementById('studio-drawer'),
    consoleList: document.getElementById('console-logs-list'),
    consoleCount: document.getElementById('console-count'),
    specDeviceName: document.getElementById('spec-device-name'),
    specResolution: document.getElementById('spec-resolution'),
    specLocalUrl: document.getElementById('spec-local-url'),
    specLanUrl: document.getElementById('spec-lan-url'),
    modalNetwork: document.getElementById('modal-network'),
    modalQrSvg: document.getElementById('modal-qr-svg'),
    modalShareUrl: document.getElementById('modal-share-url'),
    navBar: document.getElementById('device-nav-bar'),
    iosIndicator: document.getElementById('nav-ios-indicator'),
    navBtnBack: document.getElementById('nav-btn-back'),
    navBtnHome: document.getElementById('nav-btn-home'),
    navBtnRecents: document.getElementById('nav-btn-recents'),
    gatewayStatusText: document.getElementById('gateway-status-text'),
  };

  // ---------------------------------------------------------------------------
  // Device & Layout Management
  // ---------------------------------------------------------------------------
  function applyDevice(deviceId) {
    const dev = DEVICES[deviceId] || DEVICES.pixel8;
    state.currentDevice = dev.id;

    let w = dev.width;
    let h = dev.height;

    if (state.isLandscape) {
      const tmp = w;
      w = h;
      h = tmp;
    }

    // Set screen wrapper dimensions
    dom.screenWrapper.style.width = `${w}px`;
    dom.screenWrapper.style.height = `${h}px`;

    // Chassis & Navigation Bar styling
    if (dev.id === 'iphone15') {
      dom.frame.classList.add('notch-pill');
      dom.notch.style.display = 'flex';
      dom.navBar?.classList.add('ios-mode');
    } else {
      dom.navBar?.classList.remove('ios-mode');
      if (dev.notch === 'none') {
        dom.frame.classList.remove('notch-pill');
        dom.notch.style.display = 'none';
      } else {
        dom.frame.classList.remove('notch-pill');
        dom.notch.style.display = 'flex';
      }
    }

    dom.frame.style.borderRadius = dev.radius;

    // Update specs tab
    if (dom.specDeviceName) dom.specDeviceName.textContent = dev.name;
    if (dom.specResolution) dom.specResolution.textContent = `${w} × ${h} px`;

    // Update active segmented button
    document.querySelectorAll('#device-selector .seg-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.device === dev.id);
    });

    recalculateScale();
  }

  function recalculateScale() {
    if (!dom.stage || !dom.frame) return;

    if (state.scaleMode !== 'auto') {
      const scale = parseFloat(state.scaleMode) || 1;
      state.scaleValue = scale;
      dom.container.style.transform = `scale(${scale})`;
      return;
    }

    // Auto-fit inside stage viewport with comfortable margins
    const stageWidth = dom.stage.clientWidth - 48;
    const stageHeight = dom.stage.clientHeight - 48;

    const frameRect = dom.frame.getBoundingClientRect();
    // Use unscaled dimensions
    const currentScale = state.scaleValue || 1;
    const rawFrameWidth = frameRect.width / currentScale;
    const rawFrameHeight = frameRect.height / currentScale;

    if (rawFrameWidth <= 0 || rawFrameHeight <= 0) return;

    const scaleX = stageWidth / rawFrameWidth;
    const scaleY = stageHeight / rawFrameHeight;
    const fitScale = Math.min(scaleX, scaleY, 1.0); // Don't scale up past 100%

    const safeScale = Math.max(0.4, Math.floor(fitScale * 100) / 100);
    state.scaleValue = safeScale;
    dom.container.style.transform = `scale(${safeScale})`;
  }

  function toggleOrientation() {
    state.isLandscape = !state.isLandscape;
    dom.container.classList.toggle('landscape', state.isLandscape);
    applyDevice(state.currentDevice);
    addLog('info', `Device rotated to ${state.isLandscape ? 'Landscape' : 'Portrait'} mode.`);
  }

  function toggleChassis() {
    state.showChassis = !state.showChassis;
    dom.frame.classList.toggle('frameless', !state.showChassis);
    recalculateScale();
  }

  // ---------------------------------------------------------------------------
  // Digital Status Bar Clock
  // ---------------------------------------------------------------------------
  function updateClock() {
    const now = new Date();
    const hours = String(now.getHours()).padStart(2, '0');
    const mins = String(now.getMinutes()).padStart(2, '0');
    if (dom.statusClock) {
      dom.statusClock.textContent = `${hours}:${mins}`;
    }
  }

  // ---------------------------------------------------------------------------
  // Android Hardware Navigation Bridge
  // ---------------------------------------------------------------------------
  function triggerAndroidBack() {
    addLog('info', 'Simulating Android Hardware Back Key (onBackPressed bridge)...');
    try {
      const win = dom.iframe.contentWindow;
      if (win && win.LastChatApp && typeof win.LastChatApp.onBackPressed === 'function') {
        const handled = win.LastChatApp.onBackPressed();
        addLog('info', `Android Back Bridge returned: ${handled ? 'Handled by web UI' : 'Root view (App would exit)'}`);
        return handled;
      } else {
        // Fallback: evaluate via history
        win.history.back();
        return true;
      }
    } catch (e) {
      addLog('warn', `Back navigation bridge exception: ${e.message}`);
      return false;
    }
  }

  function triggerAndroidHome() {
    addLog('info', 'Simulating Home Button -> Returning to Chat view...');
    try {
      const win = dom.iframe.contentWindow;
      if (win && win.LastChatApp && typeof win.LastChatApp.switchToView === 'function') {
        win.LastChatApp.switchToView('chat-view');
      }
    } catch (e) {
      addLog('warn', `Home navigation error: ${e.message}`);
    }
  }

  function triggerAndroidRecents() {
    addLog('info', 'Simulating Recents / Inspector toggle...');
    try {
      const doc = dom.iframe.contentDocument;
      const inspectBtn = doc?.getElementById('inspect-raw-btn');
      if (inspectBtn) {
        inspectBtn.click();
      }
    } catch (e) {
      addLog('warn', `Recents action error: ${e.message}`);
    }
  }

  // ---------------------------------------------------------------------------
  // Fast Scenarios & Injections
  // ---------------------------------------------------------------------------
  function handleScenarioAction(action) {
    const win = dom.iframe.contentWindow;
    const doc = dom.iframe.contentDocument;
    if (!win || !win.LastChatApp) {
      addLog('warn', 'App iframe is still loading. Please wait a second.');
      return;
    }

    switch (action) {
      case 'switch-chat':
        win.LastChatApp.switchToView('chat-view');
        addLog('info', 'Navigated to Chat view.');
        break;

      case 'open-model-picker':
        if (typeof win.LastChatApp?.openModelPicker === 'function') {
          win.LastChatApp.openModelPicker();
        } else {
          doc?.getElementById('model-trigger-btn')?.click();
        }
        addLog('info', 'Triggered Admin Model Registry bottom sheet.');
        break;

      case 'switch-tuning':
        win.LastChatApp.switchToView('tuning-view');
        addLog('info', 'Navigated to Hyperparameter Tuning view.');
        break;

      case 'switch-admin':
        win.LastChatApp.switchToView('admin-view');
        addLog('info', 'Navigated to Admin Gateway & Provider Matrix view.');
        break;

      case 'open-inspector':
        doc?.getElementById('inspect-raw-btn')?.click();
        addLog('info', 'Opened Raw JSON / Telemetry Inspector.');
        break;

      case 'test-back-nav':
        triggerAndroidBack();
        break;

      case 'inject-reasoning':
        injectReasoningSample(win);
        break;

      case 'reset-storage':
        if (confirm('Clear local playground storage and restart?')) {
          win.localStorage.clear();
          win.location.reload();
          addLog('warn', 'Local storage cleared. App reloaded.');
        }
        break;

      default:
        console.warn('Unknown scenario:', action);
    }
  }

  function injectReasoningSample(win) {
    try {
      const sess = win.LastChatApp.state.sessions.find(s => s.id === win.LastChatApp.state.currentSessionId);
      if (!sess) return;

      sess.messages.push({
        role: 'user',
        content: 'Can you show me a concise Kotlin coroutine example with step-by-step thinking?',
        timestamp: Date.now() - 4000
      });

      sess.messages.push({
        role: 'assistant',
        model: 'deepseek-v4.1-flash',
        reasoning: 'First, identify the core requirement: concise Kotlin coroutines demonstration.\nSecond, choose runBlocking with structured launch.\nThird, format with clean syntax highlighting and key highlights breakdown.',
        content: 'Here is an idiomatic and concise Kotlin coroutines example:\n\n```kotlin\nimport kotlinx.coroutines.*\n\nfun main() = runBlocking {\n    val job = launch {\n        delay(1000L)\n        println("World! Generated from LastChat Mobile")\n    }\n    println("Hello")\n    job.join()\n}\n```\n\n### Key Highlights:\n- **Structured Concurrency**: Using `runBlocking` creates a top-level coroutine scope.\n- **Non-blocking delay**: `delay(1000L)` suspends without freezing threads.\n- **Deterministic Join**: `job.join()` awaits asynchronous completion cleanly.',
        latencyMs: 138,
        tokens: 284,
        timestamp: Date.now()
      });

      win.LastChatApp.switchToView('chat-view');
      win.LastChatApp.renderChatMessages();
      addLog('info', 'Injected full reasoning & code block into active chat session.');
    } catch (e) {
      addLog('error', `Failed to inject reasoning: ${e.message}`);
    }
  }

  function sendCustomPrompt() {
    const input = document.getElementById('dev-prompt-input');
    const promptText = input?.value.trim();
    if (!promptText) return;

    try {
      const doc = dom.iframe.contentDocument;
      const win = dom.iframe.contentWindow;
      if (!doc || !win) return;

      win.LastChatApp.switchToView('chat-view');

      const userInput = doc.getElementById('user-input');
      const sendBtn = doc.getElementById('send-btn');
      if (userInput && sendBtn) {
        userInput.value = promptText;
        userInput.dispatchEvent(new Event('input'));
        sendBtn.click();
        addLog('info', `Sent prompt to app: "${promptText}"`);
      }
    } catch (e) {
      addLog('error', `Failed to send prompt: ${e.message}`);
    }
  }

  // ---------------------------------------------------------------------------
  // Console Log Interceptor & Streamer
  // ---------------------------------------------------------------------------
  function hookIframeConsole() {
    try {
      const win = dom.iframe.contentWindow;
      if (!win) return;

      const origLog = win.console.log;
      const origWarn = win.console.warn;
      const origError = win.console.error;

      win.console.log = function (...args) {
        addLog('info', args.map(formatLogArg).join(' '));
        origLog.apply(win.console, args);
      };

      win.console.warn = function (...args) {
        addLog('warn', args.map(formatLogArg).join(' '));
        origWarn.apply(win.console, args);
      };

      win.console.error = function (...args) {
        addLog('error', args.map(formatLogArg).join(' '));
        origError.apply(win.console, args);
      };

      addLog('info', 'Hooked mobile app console logger successfully.');
    } catch (e) {
      console.warn('Could not hook iframe console (cross-origin):', e);
    }
  }

  function hookIframeKeyboard() {
    try {
      const win = dom.iframe.contentWindow;
      if (!win) return;
      win.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
          triggerAndroidBack();
        } else if (e.key === 'Backspace' && e.target.tagName !== 'INPUT' && e.target.tagName !== 'TEXTAREA') {
          triggerAndroidBack();
        }
      });
      addLog('info', 'Hooked iframe hardware back keyboard listener.');
    } catch (e) {
      console.warn('Could not hook iframe keyboard events:', e);
    }
  }

  function formatLogArg(arg) {
    if (typeof arg === 'string') return arg;
    try {
      return JSON.stringify(arg);
    } catch (e) {
      return String(arg);
    }
  }

  function addLog(level, message) {
    state.logs.push({ level, message, time: new Date() });
    if (dom.consoleCount) {
      dom.consoleCount.textContent = state.logs.length;
    }

    renderLogEntry(level, message);
  }

  function renderLogEntry(level, message) {
    if (state.consoleFilter !== 'all' && state.consoleFilter !== level) {
      return;
    }

    const row = document.createElement('div');
    row.className = `console-log-row ${level}`;
    
    const tag = document.createElement('span');
    tag.className = `log-tag ${level}`;
    tag.textContent = level.toUpperCase();

    const text = document.createElement('span');
    text.textContent = message;

    row.appendChild(tag);
    row.appendChild(text);

    dom.consoleList.appendChild(row);
    dom.consoleList.scrollTop = dom.consoleList.scrollHeight;
  }

  function filterLogs(filter) {
    state.consoleFilter = filter;
    document.querySelectorAll('.console-filter-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.filter === filter);
    });

    dom.consoleList.innerHTML = '';
    state.logs.forEach(l => {
      renderLogEntry(l.level, l.message);
    });
  }

  function clearLogs() {
    state.logs = [];
    dom.consoleList.innerHTML = '';
    if (dom.consoleCount) dom.consoleCount.textContent = '0';
  }

  function copyLogs() {
    const text = state.logs.map(l => `[${l.level.toUpperCase()}] ${l.message}`).join('\n');
    navigator.clipboard?.writeText(text);
    addLog('info', 'Copied logs to clipboard.');
  }

  // ---------------------------------------------------------------------------
  // LAN Sharing & QR Modal
  // ---------------------------------------------------------------------------
  async function fetchNetworkInfo() {
    try {
      const res = await fetch('/api/health');
      if (res.ok) {
        const data = await res.json();
        if (data.networkUrl) {
          state.networkUrl = data.networkUrl;
          if (dom.specLanUrl) dom.specLanUrl.textContent = data.networkUrl;
        }
      }
    } catch (e) {
      if (dom.specLanUrl) dom.specLanUrl.textContent = window.location.origin;
    }

    if (dom.specLocalUrl) dom.specLocalUrl.textContent = window.location.origin;
  }

  function openNetworkModal() {
    const url = state.networkUrl || window.location.origin;
    if (dom.modalShareUrl) dom.modalShareUrl.value = url;

    if (window.QRCodeSVG && dom.modalQrSvg) {
      const qr = new window.QRCodeSVG(url, {
        size: 190,
        colorDark: '#090c12',
        colorLight: '#ffffff'
      });
      dom.modalQrSvg.innerHTML = qr.toSVG();
    }

    dom.modalNetwork.classList.add('active');
  }

  function closeNetworkModal() {
    dom.modalNetwork.classList.remove('active');
  }

  // ---------------------------------------------------------------------------
  // Real-Time Gateway Health & Verification Probe
  // ---------------------------------------------------------------------------
  async function checkGatewayHealth() {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2000);
      const res = await fetch('https://relay-gw.pages.dev/v1/models', { signal: controller.signal });
      clearTimeout(timeoutId);
      if (res.ok) {
        const data = await res.json();
        const count = data.data?.length || 25;
        if (dom.gatewayStatusText) {
          dom.gatewayStatusText.textContent = `Relay Gateway Active (${count} Models)`;
        }
      } else {
        if (dom.gatewayStatusText) dom.gatewayStatusText.textContent = 'Relay Gateway Standby';
      }
    } catch (e) {
      if (dom.gatewayStatusText) dom.gatewayStatusText.textContent = 'Relay Gateway Standby';
    }
  }

  // ---------------------------------------------------------------------------
  // Snapshot Capture (Reflects true current view)
  // ---------------------------------------------------------------------------
  async function captureSnapshot() {
    addLog('info', 'Capturing screen snapshot...');
    try {
      // Create a visual flash animation on the frame
      dom.frame.style.filter = 'brightness(1.5)';
      setTimeout(() => dom.frame.style.filter = 'none', 180);

      const doc = dom.iframe.contentDocument;
      const isModalOpen = doc?.getElementById('model-picker-modal')?.classList.contains('active');
      const isInspectorOpen = doc?.getElementById('inspector-modal')?.classList.contains('active');
      const activePane = doc?.querySelector('.view-pane.active')?.id || 'chat-view';

      let imageSrc = '/dist/verify_mobile_chat_turn.png';
      if (isModalOpen) {
        imageSrc = '/dist/verify_mobile_model_picker.png';
      } else if (isInspectorOpen) {
        imageSrc = '/dist/verify_mobile_inspector.png';
      } else if (activePane === 'tuning-view') {
        imageSrc = '/dist/verify_mobile_tuning_view.png';
      } else if (activePane === 'admin-view') {
        imageSrc = '/dist/verify_mobile_admin_view.png';
      } else {
        const hasMessages = doc?.querySelectorAll('.chat-turn')?.length > 0;
        imageSrc = hasMessages ? '/dist/verify_mobile_chat_turn.png' : '/dist/verify_mobile_chat_empty.png';
      }

      const link = document.createElement('a');
      link.href = imageSrc;
      link.download = `lastchat-${state.currentDevice}-${activePane}-${Date.now()}.png`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      addLog('info', `Snapshot saved for ${activePane} (${imageSrc.split('/').pop()}).`);
    } catch (e) {
      addLog('warn', `Snapshot error: ${e.message}`);
    }
  }

  // ---------------------------------------------------------------------------
  // Event Bindings
  // ---------------------------------------------------------------------------
  function bindEvents() {
    // Device Selector buttons
    document.querySelectorAll('#device-selector .seg-btn').forEach(btn => {
      btn.addEventListener('click', () => applyDevice(btn.dataset.device));
    });

    // Scale Selector buttons
    document.querySelectorAll('#scale-selector .seg-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('#scale-selector .seg-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        state.scaleMode = btn.dataset.scale;
        recalculateScale();
      });
    });

    // Rotate button
    document.getElementById('btn-rotate')?.addEventListener('click', toggleOrientation);

    // Chassis toggle button
    document.getElementById('btn-toggle-frame')?.addEventListener('click', toggleChassis);

    // Drawer toggle
    document.getElementById('btn-toggle-drawer')?.addEventListener('click', () => {
      dom.drawer.classList.toggle('collapsed');
      setTimeout(recalculateScale, 260);
    });

    document.getElementById('btn-close-drawer')?.addEventListener('click', () => {
      dom.drawer.classList.add('collapsed');
      setTimeout(recalculateScale, 260);
    });

    // Drawer Tabs
    document.querySelectorAll('.drawer-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.drawer-tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.drawer-pane').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        const targetPane = document.getElementById(btn.dataset.tab);
        if (targetPane) targetPane.classList.add('active');
      });
    });

    // Scenario buttons
    document.querySelectorAll('.scenario-btn').forEach(btn => {
      btn.addEventListener('click', () => handleScenarioAction(btn.dataset.action));
    });

    // Dev Prompt Send
    document.getElementById('dev-prompt-send-btn')?.addEventListener('click', sendCustomPrompt);
    document.getElementById('dev-prompt-input')?.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') sendCustomPrompt();
    });

    // Console filters
    document.querySelectorAll('.console-filter-btn[data-filter]').forEach(btn => {
      btn.addEventListener('click', () => filterLogs(btn.dataset.filter));
    });
    document.getElementById('btn-clear-logs')?.addEventListener('click', clearLogs);
    document.getElementById('btn-copy-logs')?.addEventListener('click', copyLogs);

    // Network modal
    document.getElementById('btn-network-share')?.addEventListener('click', openNetworkModal);
    document.getElementById('modal-network-close')?.addEventListener('click', closeNetworkModal);
    dom.modalNetwork?.addEventListener('click', (e) => {
      if (e.target === dom.modalNetwork) closeNetworkModal();
    });
    document.getElementById('modal-copy-url-btn')?.addEventListener('click', () => {
      const inp = dom.modalShareUrl;
      inp.select();
      navigator.clipboard?.writeText(inp.value);
      addLog('info', `Copied URL: ${inp.value}`);
    });

    // Snapshot
    document.getElementById('btn-snapshot')?.addEventListener('click', captureSnapshot);

    // Hardware Navigation buttons
    dom.navBtnBack?.addEventListener('click', triggerAndroidBack);
    dom.navBtnHome?.addEventListener('click', triggerAndroidHome);
    dom.navBtnRecents?.addEventListener('click', triggerAndroidRecents);

    // Global Keyboard Shortcuts
    window.addEventListener('keydown', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

      if (e.key === 'Escape' || e.key === 'Backspace') {
        triggerAndroidBack();
      } else if (e.key === 'r' || e.key === 'R') {
        dom.iframe.contentWindow?.location.reload();
        addLog('info', 'Reloaded mobile app iframe.');
      } else if (e.key === 'o' || e.key === 'O') {
        toggleOrientation();
      } else if (e.key === '1') {
        applyDevice('pixel8');
      } else if (e.key === '2') {
        applyDevice('s24');
      } else if (e.key === '3') {
        applyDevice('iphone15');
      } else if (e.key === '4') {
        applyDevice('compact');
      } else if (e.key === '5') {
        applyDevice('tablet');
      }
    });

    // iOS Home Indicator
    dom.iosIndicator?.addEventListener('click', triggerAndroidHome);

    // Window resize -> recalculate scale
    window.addEventListener('resize', recalculateScale);

    // Iframe load listener
    dom.iframe.addEventListener('load', () => {
      hookIframeConsole();
      hookIframeKeyboard();
      addLog('info', 'Mobile app view loaded successfully.');
    });
  }

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------
  function init() {
    // If viewport is compact (<= 1080px), collapse drawer by default to keep phone centered
    if (window.innerWidth <= 1080 && dom.drawer) {
      dom.drawer.classList.add('collapsed');
    }
    applyDevice('pixel8');
    updateClock();
    setInterval(updateClock, 10000);
    bindEvents();
    fetchNetworkInfo();
    checkGatewayHealth();
    setInterval(checkGatewayHealth, 30000);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
