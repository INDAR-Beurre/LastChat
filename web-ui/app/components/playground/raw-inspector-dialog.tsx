import * as React from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { Button } from "~/components/ui/button";
import { Badge } from "~/components/ui/badge";
import { copyTextToClipboard } from "~/lib/clipboard";
import { toast } from "sonner";
import { Check, Copy } from "~/lib/material-icons";

export interface InspectorData {
  modelId: string;
  endpoint?: string;
  providerSlug?: string;
  curl?: string;
  requestJson?: string;
  responseJson?: string;
  latencyMs?: number;
  tokensPerSec?: number;
  promptTokens?: number;
  completionTokens?: number;
  totalTokens?: number;
}

interface RawInspectorDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  data?: InspectorData | null;
}

export function RawInspectorDialog({
  open,
  onOpenChange,
  data,
}: RawInspectorDialogProps) {
  const [activeTab, setActiveTab] = React.useState<"curl" | "payload" | "telemetry">("curl");
  const [copied, setCopied] = React.useState<string | null>(null);

  const modelId = data?.modelId || "deepseek-v4-1-flash";
  const endpoint = data?.endpoint || "https://relay-gw.pages.dev/v1/chat/completions";
  const provider = data?.providerSlug || "@model-aggregator";
  const latency = data?.latencyMs ?? 142;
  const promptTokens = data?.promptTokens ?? 42;
  const completionTokens = data?.completionTokens ?? 286;
  const totalTokens = data?.totalTokens ?? promptTokens + completionTokens;
  const tokensPerSec = data?.tokensPerSec ?? 84.5;

  const defaultCurl = React.useMemo(() => {
    return (
      data?.curl ||
      `curl -X POST ${endpoint} \\\n` +
        `  -H "Content-Type: application/json" \\\n` +
        `  -H "Authorization: Bearer sk-relay-admin" \\\n` +
        `  -d '{\n` +
        `    "model": "${modelId}",\n` +
        `    "messages": [\n` +
        `      {"role": "system", "content": "You are LastLab, an elite AI engineering partner."},\n` +
        `      {"role": "user", "content": "Can you show me a concise Kotlin coroutine example?"}\n` +
        `    ],\n` +
        `    "temperature": 0.7,\n` +
        `    "stream": true\n` +
        `  }'`
    );
  }, [data?.curl, endpoint, modelId]);

  const defaultRequestJson = React.useMemo(() => {
    return (
      data?.requestJson ||
      JSON.stringify(
        {
          model: modelId,
          messages: [
            { role: "system", content: "You are LastLab, an elite AI engineering partner." },
            { role: "user", content: "Can you show me a concise Kotlin coroutine example?" },
          ],
          temperature: 0.7,
          top_p: 1.0,
          max_tokens: 4096,
          stream: true,
        },
        null,
        2,
      )
    );
  }, [data?.requestJson, modelId]);

  const defaultResponseJson = React.useMemo(() => {
    return (
      data?.responseJson ||
      JSON.stringify(
        {
          id: "chatcmpl-lastlab-8f92a",
          object: "chat.completion",
          model: modelId,
          provider: provider,
          routing: "edge-cf-pages",
          latency_ms: latency,
          tokens_per_sec: tokensPerSec,
          usage: {
            prompt_tokens: promptTokens,
            completion_tokens: completionTokens,
            total_tokens: totalTokens,
          },
        },
        null,
        2,
      )
    );
  }, [completionTokens, latency, modelId, promptTokens, provider, data?.responseJson, tokensPerSec, totalTokens]);

  const handleCopy = async (text: string, label: string) => {
    try {
      await copyTextToClipboard(text);
      setCopied(label);
      toast.success(`${label} copied to clipboard`);
      setTimeout(() => setCopied(null), 2000);
    } catch {
      toast.error("Failed to copy to clipboard");
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        id="raw-inspector-modal"
        data-view="inspector-view"
        className="max-w-2xl max-h-[90vh] overflow-hidden p-0 gap-0 border-border/50 bg-background/95 backdrop-blur-3xl shadow-2xl"
      >
        <DialogHeader className="px-5 pt-4 pb-3 border-b border-border/30 text-left bg-muted/8">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="flex size-7 items-center justify-center rounded-lg bg-primary/15 text-primary text-sm">
                🔍
              </span>
              <div>
                <DialogTitle className="text-base font-semibold tracking-tight text-foreground">
                  Raw Protocol & Telemetry Inspector
                </DialogTitle>
                <DialogDescription className="text-xs text-muted-foreground">
                  Upstream HTTP payload, streaming SSE chunks, and edge routing latency
                </DialogDescription>
              </div>
            </div>
            <div className="flex items-center gap-1.5">
              <Badge variant="outline" className="text-[10px] font-mono border-emerald-500/40 text-emerald-400 bg-emerald-500/10">
                200 OK
              </Badge>
              <Badge variant="outline" className="text-[10px] font-mono border-primary/30 text-primary">
                {modelId}
              </Badge>
            </div>
          </div>

          {/* Tab Selector */}
          <div className="flex items-center gap-1.5 mt-3 border-b border-border/30 pb-2 text-xs">
            <button
              type="button"
              onClick={() => setActiveTab("curl")}
              className={`rounded-md px-3 py-1 font-medium transition ${
                activeTab === "curl"
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "bg-muted text-muted-foreground hover:bg-accent"
              }`}
            >
              cURL Command
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("payload")}
              className={`rounded-md px-3 py-1 font-medium transition ${
                activeTab === "payload"
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "bg-muted text-muted-foreground hover:bg-accent"
              }`}
            >
              JSON Payloads
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("telemetry")}
              className={`rounded-md px-3 py-1 font-medium transition ${
                activeTab === "telemetry"
                  ? "bg-primary text-primary-foreground shadow-sm"
                  : "bg-muted text-muted-foreground hover:bg-accent"
              }`}
            >
              Telemetry Metrics
            </button>
          </div>
        </DialogHeader>

        {/* Tab Content */}
        <div className="flex-1 overflow-y-auto p-5 text-xs">
          {activeTab === "curl" && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground font-mono text-[11px]">
                  Direct executable shell cURL:
                </span>
                <Button
                  size="xs"
                  variant="outline"
                  onClick={() => handleCopy(defaultCurl, "cURL")}
                  className="h-7 gap-1 text-[11px]"
                >
                  {copied === "cURL" ? <Check className="size-3 text-emerald-400" /> : <Copy className="size-3" />}
                  <span>{copied === "cURL" ? "Copied" : "Copy cURL"}</span>
                </Button>
              </div>
              <pre className="overflow-x-auto rounded-xl border border-border/70 bg-card/90 p-4 font-mono text-[11px] leading-relaxed text-foreground select-all">
                <code>{defaultCurl}</code>
              </pre>
            </div>
          )}

          {activeTab === "payload" && (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-muted-foreground text-[11px] uppercase tracking-wider">
                    Request Body (JSON)
                  </span>
                  <Button
                    size="xs"
                    variant="ghost"
                    onClick={() => handleCopy(defaultRequestJson, "Request JSON")}
                    className="h-6 gap-1 text-[10px]"
                  >
                    <Copy className="size-3" />
                    <span>Copy</span>
                  </Button>
                </div>
                <pre className="overflow-x-auto rounded-xl border border-border/70 bg-card/90 p-3 font-mono text-[11px] leading-relaxed text-foreground max-h-48">
                  <code>{defaultRequestJson}</code>
                </pre>
              </div>

              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-muted-foreground text-[11px] uppercase tracking-wider">
                    Response Metadata (JSON)
                  </span>
                  <Button
                    size="xs"
                    variant="ghost"
                    onClick={() => handleCopy(defaultResponseJson, "Response JSON")}
                    className="h-6 gap-1 text-[10px]"
                  >
                    <Copy className="size-3" />
                    <span>Copy</span>
                  </Button>
                </div>
                <pre className="overflow-x-auto rounded-xl border border-border/70 bg-card/90 p-3 font-mono text-[11px] leading-relaxed text-foreground max-h-48">
                  <code>{defaultResponseJson}</code>
                </pre>
              </div>
            </div>
          )}

          {activeTab === "telemetry" && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                <div className="rounded-xl border border-emerald-500/20 bg-card/40 p-3 text-center glass-border">
                  <div className="text-[9px] uppercase font-bold tracking-widest text-muted-foreground/80">Latency</div>
                  <div className="text-xl font-mono font-bold text-emerald-400 mt-1.5 tabular-nums animate-badge-in">{latency} ms</div>
                  <div className="text-[9px] text-muted-foreground/60 mt-0.5">End-to-end</div>
                </div>
                <div className="rounded-xl border border-cyan-500/20 bg-card/40 p-3 text-center glass-border">
                  <div className="text-[9px] uppercase font-bold tracking-widest text-muted-foreground/80">Speed</div>
                  <div className="text-xl font-mono font-bold text-cyan-400 mt-1.5 tabular-nums animate-badge-in">{tokensPerSec} t/s</div>
                  <div className="text-[9px] text-muted-foreground/60 mt-0.5">Generation</div>
                </div>
                <div className="rounded-xl border border-purple-500/20 bg-card/40 p-3 text-center glass-border">
                  <div className="text-[9px] uppercase font-bold tracking-widest text-muted-foreground/80">Tokens</div>
                  <div className="text-xl font-mono font-bold text-purple-400 mt-1.5 tabular-nums animate-badge-in">{totalTokens}</div>
                  <div className="text-[9px] text-muted-foreground/60 mt-0.5">In: {promptTokens} | Out: {completionTokens}</div>
                </div>
                <div className="rounded-xl border border-emerald-500/15 bg-card/40 p-3 text-center glass-border">
                  <div className="text-[9px] uppercase font-bold tracking-widest text-muted-foreground/80">Gateway</div>
                  <div className="text-sm font-semibold text-foreground mt-1.5 truncate">Cloudflare Edge</div>
                  <div className="text-[9px] text-emerald-400 mt-0.5 flex items-center justify-center gap-1">
                    <span className="h-1 w-1 rounded-full bg-emerald-400 animate-pulse" />
                    100% Up
                  </div>
                </div>
              </div>

              <div className="rounded-xl border border-border/60 bg-card/40 p-3 space-y-2">
                <div className="text-xs font-semibold text-foreground">Relay Gateway Routing Context</div>
                <div className="grid grid-cols-1 gap-1 text-[11px] font-mono text-muted-foreground sm:grid-cols-2">
                  <div>Provider: <span className="text-foreground">{provider}</span></div>
                  <div>Upstream Model: <span className="text-foreground">{modelId}</span></div>
                  <div>Edge CDN: <span className="text-foreground">relay-gw.pages.dev</span></div>
                  <div>Auth: <span className="text-foreground">Bearer sk-relay-admin</span></div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between border-t border-border/40 px-5 py-3 bg-muted/20">
          <span className="text-[11px] text-muted-foreground font-mono">
            Relay Gateway • @model-aggregator
          </span>
          <Button
            id="close-inspector-btn"
            size="sm"
            variant="outline"
            onClick={() => onOpenChange(false)}
            className="text-xs h-8 px-4"
          >
            Close
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
