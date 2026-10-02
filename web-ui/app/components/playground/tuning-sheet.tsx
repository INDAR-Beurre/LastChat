import * as React from "react";
import {
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "~/components/ui/drawer";
import { Button } from "~/components/ui/button";
import { Badge } from "~/components/ui/badge";
import { cn } from "~/lib/utils";
import { toast } from "sonner";

export interface PlaygroundParameters {
  temperature: number;
  topP: number;
  reasoningEffort: "none" | "low" | "medium" | "high";
  maxTokens: number;
  systemPrompt: string;
}

export const DEFAULT_PARAMETERS: PlaygroundParameters = {
  temperature: 0.7,
  topP: 1.0,
  reasoningEffort: "medium",
  maxTokens: 4096,
  systemPrompt:
    "You are LastLab, an elite AI engineering partner powered exclusively by the Relay Gateway (@model-aggregator). Deliver clear, structured, and production-grade solutions.",
};

export const SYSTEM_PROMPT_PRESETS: Array<{
  id: string;
  name: string;
  icon: string;
  badge: string;
  prompt: string;
  description: string;
}> = [
  {
    id: "general",
    name: "General Partner",
    icon: "⚡",
    badge: "Balanced",
    description: "Versatile conversational assistant with balanced reasoning.",
    prompt:
      "You are LastLab, an elite AI engineering partner powered exclusively by the Relay Gateway (@model-aggregator). Deliver clear, structured, and production-grade solutions.",
  },
  {
    id: "architect",
    name: "Software Architect",
    icon: "🏗️",
    badge: "Production",
    description: "Senior systems architect for Kotlin, TypeScript, and distributed systems.",
    prompt:
      "You are a Principal Software Architect. Design clean, scalable, decoupled systems with idiomatic Kotlin, TypeScript, and modern distributed paradigms. Emphasize deterministic concurrency, low memory footprint, and comprehensive error boundaries.",
  },
  {
    id: "reasoner",
    name: "Deep Reasoner",
    icon: "🧠",
    badge: "Formal Logic",
    description: "Exhaustive step-by-step logical proofs and edge-case verification.",
    prompt:
      "You are a rigorous mathematical and logical reasoning engine. Deconstruct complex problems into first principles, show step-by-step deductive logic, and verify all edge cases before presenting final conclusions.",
  },
  {
    id: "coder",
    name: "Fast Systems Coder",
    icon: "⚡",
    badge: "Ultra-Concise",
    description: "Zero conversational filler, pure production code and tests.",
    prompt:
      "You are an ultra-fast senior systems programmer. Deliver clean, highly optimized, idiomatic code with zero conversational fluff. Always include boundary checks and concise explanatory comments.",
  },
];

const REASONING_COLORS = {
  none: "bg-muted/80 text-muted-foreground",
  low: "bg-blue-600/90 text-white",
  medium: "bg-purple-600/90 text-white",
  high: "bg-rose-600/90 text-white",
} as const;

const TOKEN_PRESETS = [
  { value: 2048, label: "2k", sublabel: "Quick" },
  { value: 4096, label: "4k", sublabel: "Standard" },
  { value: 16384, label: "16k", sublabel: "Extended" },
  { value: 64000, label: "64k", sublabel: "Maximum" },
] as const;

interface PlaygroundTuningSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  params?: PlaygroundParameters;
  onApply?: (params: PlaygroundParameters) => void;
}

export function PlaygroundTuningSheet({
  open,
  onOpenChange,
  params: initialParams,
  onApply,
}: PlaygroundTuningSheetProps) {
  const [params, setParams] = React.useState<PlaygroundParameters>(
    () => initialParams ?? DEFAULT_PARAMETERS,
  );
  const [activePreset, setActivePreset] = React.useState<string>("general");

  React.useEffect(() => {
    if (initialParams) {
      setParams(initialParams);
    }
  }, [initialParams]);

  const handleSelectPreset = (presetId: string) => {
    const preset = SYSTEM_PROMPT_PRESETS.find((p) => p.id === presetId);
    if (!preset) return;
    setActivePreset(preset.id);
    setParams((prev) => ({ ...prev, systemPrompt: preset.prompt }));
    toast.success(`Preset "${preset.name}" applied`);
  };

  const handleReset = () => {
    setParams(DEFAULT_PARAMETERS);
    setActivePreset("general");
    toast.info("Playground parameters reset to defaults");
  };

  const handleSave = () => {
    onApply?.(params);
    onOpenChange(false);
    toast.success("Parameters applied to active playground session");
  };

  /* Temperature semantic label */
  const tempLabel =
    params.temperature <= 0.3
      ? "Precise"
      : params.temperature <= 0.8
        ? "Balanced"
        : params.temperature <= 1.3
          ? "Creative"
          : "Wild";

  return (
    <Drawer open={open} onOpenChange={onOpenChange}>
      <DrawerContent
        id="playground-tuning-drawer"
        data-view="tuning-view"
        className="max-h-[92vh] overflow-hidden border-border/60 bg-background/95 backdrop-blur-2xl shadow-2xl"
      >
        <div className="mx-auto w-full max-w-2xl flex flex-col h-full max-h-[92vh]">
          {/* Header */}
          <DrawerHeader className="px-5 pt-4 pb-2.5 border-b border-border/30 text-left">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <span className="flex size-8 items-center justify-center rounded-xl bg-primary/12 text-primary text-base ring-1 ring-primary/20">
                  🎛️
                </span>
                <div>
                  <DrawerTitle className="text-[15px] font-semibold tracking-tight text-foreground">
                    Hyperparameter & System Tuning
                  </DrawerTitle>
                  <DrawerDescription className="text-[11px] text-muted-foreground mt-0.5">
                    Fine-tune runtime sampling, reasoning intensity, and system persona
                  </DrawerDescription>
                </div>
              </div>
              <Badge
                variant="outline"
                className="text-[10px] font-mono border-primary/25 text-primary bg-primary/8 px-2"
              >
                @model-aggregator
              </Badge>
            </div>
          </DrawerHeader>

          {/* Scrollable Content Body */}
          <div className="flex-1 overflow-y-auto px-5 py-4 space-y-5">
            {/* 1. System Prompt Presets */}
            <div className="space-y-2.5">
              <div className="flex items-center justify-between">
                <label className="text-[11px] font-semibold uppercase tracking-widest text-muted-foreground">
                  System Persona Presets
                </label>
                <span className="text-[10px] text-muted-foreground/70 font-medium">
                  1-Tap Quick Load
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                {SYSTEM_PROMPT_PRESETS.map((preset) => {
                  const isSelected = activePreset === preset.id;
                  return (
                    <button
                      key={preset.id}
                      type="button"
                      data-preset={preset.id}
                      data-no-touch-enforce
                      onClick={() => handleSelectPreset(preset.id)}
                      className={cn(
                        "flex flex-col items-start gap-1.5 rounded-xl border p-3 text-left transition-all duration-200",
                        "hover:border-primary/40 hover:bg-accent/50 active:scale-[0.97]",
                        isSelected
                          ? "border-primary/50 bg-primary/8 shadow-sm ring-1 ring-primary/20"
                          : "border-border/50 bg-card/40",
                      )}
                    >
                      <div className="flex w-full items-center justify-between">
                        <span className="text-lg leading-none">{preset.icon}</span>
                        <span
                          className={cn(
                            "rounded-full px-1.5 py-px text-[9px] font-medium transition-colors",
                            isSelected
                              ? "bg-primary text-primary-foreground font-semibold"
                              : "bg-muted/80 text-muted-foreground",
                          )}
                        >
                          {preset.badge}
                        </span>
                      </div>
                      <span className="text-xs font-medium text-foreground line-clamp-1">
                        {preset.name}
                      </span>
                      <span className="text-[10px] text-muted-foreground/80 line-clamp-2 leading-snug">
                        {preset.description}
                      </span>
                    </button>
                  );
                })}
              </div>

              {/* System Prompt Textarea */}
              <div className="mt-2.5 space-y-1.5">
                <label className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground">
                  Active System Instructions
                </label>
                <textarea
                  id="system-prompt-input"
                  value={params.systemPrompt}
                  onChange={(e) => {
                    setParams((prev) => ({ ...prev, systemPrompt: e.target.value }));
                    setActivePreset("custom");
                  }}
                  rows={3}
                  placeholder="Enter custom system instructions..."
                  className="w-full resize-none rounded-xl border border-border/50 bg-muted/30 p-3 text-xs leading-relaxed text-foreground placeholder:text-muted-foreground/60 focus:border-primary/50 focus:outline-none focus:ring-2 focus:ring-primary/15 transition-all"
                />
              </div>
            </div>

            {/* 2. Sampling Controls: Temperature & Top-P */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {/* Temperature */}
              <div className="space-y-2.5 rounded-xl border border-border/40 bg-card/30 p-3.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-medium text-foreground flex items-center gap-1.5">
                    <span>🌡️ Temperature</span>
                  </label>
                  <div className="flex items-center gap-1.5">
                    <span className="text-[9px] text-muted-foreground font-medium">
                      {tempLabel}
                    </span>
                    <span className="rounded-md bg-primary/12 px-2 py-0.5 text-xs font-mono font-bold text-primary tabular-nums ring-1 ring-primary/15">
                      {params.temperature.toFixed(2)}
                    </span>
                  </div>
                </div>
                <input
                  type="range"
                  min="0"
                  max="2"
                  step="0.05"
                  value={params.temperature}
                  onChange={(e) =>
                    setParams((prev) => ({ ...prev, temperature: parseFloat(e.target.value) }))
                  }
                  className="h-1.5 w-full cursor-pointer appearance-none rounded-lg bg-muted accent-primary"
                />
                <div className="flex items-center justify-between text-[10px] text-muted-foreground/80">
                  <button
                    type="button"
                    data-no-touch-enforce
                    onClick={() => setParams((p) => ({ ...p, temperature: 0.2 }))}
                    className="hover:text-primary transition rounded-md px-1.5 py-0.5 hover:bg-primary/8"
                  >
                    🎯 0.2
                  </button>
                  <button
                    type="button"
                    data-no-touch-enforce
                    onClick={() => setParams((p) => ({ ...p, temperature: 0.7 }))}
                    className="hover:text-primary transition font-medium rounded-md px-1.5 py-0.5 hover:bg-primary/8"
                  >
                    ⚖️ 0.7
                  </button>
                  <button
                    type="button"
                    data-no-touch-enforce
                    onClick={() => setParams((p) => ({ ...p, temperature: 1.2 }))}
                    className="hover:text-primary transition rounded-md px-1.5 py-0.5 hover:bg-primary/8"
                  >
                    🎨 1.2
                  </button>
                </div>
              </div>

              {/* Top-P */}
              <div className="space-y-2.5 rounded-xl border border-border/40 bg-card/30 p-3.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-medium text-foreground flex items-center gap-1.5">
                    <span>🎯 Top-P (Nucleus)</span>
                  </label>
                  <span className="rounded-md bg-primary/12 px-2 py-0.5 text-xs font-mono font-bold text-primary tabular-nums ring-1 ring-primary/15">
                    {params.topP.toFixed(2)}
                  </span>
                </div>
                <input
                  type="range"
                  min="0.05"
                  max="1.0"
                  step="0.05"
                  value={params.topP}
                  onChange={(e) =>
                    setParams((prev) => ({ ...prev, topP: parseFloat(e.target.value) }))
                  }
                  className="h-1.5 w-full cursor-pointer appearance-none rounded-lg bg-muted accent-primary"
                />
                <div className="flex items-center justify-between text-[10px] text-muted-foreground/80">
                  <span>0.05 — Focused</span>
                  <span className="font-medium">0.95 — Recommended</span>
                  <span>1.0 — Full</span>
                </div>
              </div>
            </div>

            {/* 3. Reasoning Effort & Token Budget */}
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              {/* Reasoning Effort */}
              <div className="space-y-2.5 rounded-xl border border-border/40 bg-card/30 p-3.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-medium text-foreground flex items-center gap-1.5">
                    <span>🧠 Reasoning Effort</span>
                  </label>
                  <span className="text-[9px] text-purple-400/80 font-mono font-medium">
                    DeepSeek / Astra
                  </span>
                </div>
                <div className="grid grid-cols-4 gap-1.5">
                  {(["none", "low", "medium", "high"] as const).map((effort) => {
                    const isSelected = params.reasoningEffort === effort;
                    return (
                      <button
                        key={effort}
                        type="button"
                        data-no-touch-enforce
                        onClick={() => setParams((p) => ({ ...p, reasoningEffort: effort }))}
                        className={cn(
                          "rounded-lg py-2 text-[11px] font-semibold capitalize transition-all duration-200",
                          isSelected
                            ? cn(REASONING_COLORS[effort], "shadow-sm ring-1 ring-white/10")
                            : "bg-muted/60 text-muted-foreground hover:bg-accent/80",
                        )}
                      >
                        {effort}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Max Output Tokens */}
              <div className="space-y-2.5 rounded-xl border border-border/40 bg-card/30 p-3.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-medium text-foreground flex items-center gap-1.5">
                    <span>📊 Max Output Tokens</span>
                  </label>
                  <span className="rounded-md bg-cyan-500/12 px-2 py-0.5 text-xs font-mono font-bold text-cyan-400 tabular-nums ring-1 ring-cyan-500/20">
                    {params.maxTokens >= 1000
                      ? `${Math.round(params.maxTokens / 1024)}k tok`
                      : `${params.maxTokens} tok`}
                  </span>
                </div>
                <div className="grid grid-cols-4 gap-1.5">
                  {TOKEN_PRESETS.map(({ value, label, sublabel }) => {
                    const isSelected = params.maxTokens === value;
                    return (
                      <button
                        key={value}
                        type="button"
                        data-no-touch-enforce
                        onClick={() => setParams((p) => ({ ...p, maxTokens: value }))}
                        className={cn(
                          "flex flex-col items-center rounded-lg py-2 transition-all duration-200",
                          isSelected
                            ? "bg-primary text-primary-foreground font-semibold shadow-sm ring-1 ring-primary/30"
                            : "bg-muted/60 text-muted-foreground hover:bg-accent/80",
                        )}
                      >
                        <span className="text-[11px] font-mono font-bold">{label}</span>
                        <span className={cn("text-[8px]", isSelected ? "text-primary-foreground/70" : "text-muted-foreground/60")}>
                          {sublabel}
                        </span>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Footer Actions */}
          <DrawerFooter className="px-5 py-3 border-t border-border/30 flex-row items-center justify-between gap-2 bg-muted/10">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleReset}
              className="text-xs h-9 border-border/50"
            >
              Reset Defaults
            </Button>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => onOpenChange(false)}
                className="text-xs h-9"
              >
                Cancel
              </Button>
              <Button
                type="button"
                size="sm"
                onClick={handleSave}
                className="text-xs h-9 bg-primary text-primary-foreground px-5 font-semibold shadow-sm hover:bg-primary/90 transition-all"
              >
                Apply to Session
              </Button>
            </div>
          </DrawerFooter>
        </div>
      </DrawerContent>
    </Drawer>
  );
}
