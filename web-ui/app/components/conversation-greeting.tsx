import * as React from "react";

import { useTranslation } from "react-i18next";

import { cn } from "~/lib/utils";

type GreetingBucket =
  | "early_morning"
  | "morning"
  | "lunch"
  | "afternoon"
  | "evening"
  | "late_evening"
  | "night"
  | "early_hours";

const GREETING_DEFAULTS: Record<GreetingBucket, string> = {
  early_morning: "Rise and shine!",
  morning: "Good Morning!",
  lunch: "Lunch time!",
  afternoon: "Good Afternoon!",
  evening: "Good Evening!",
  late_evening: "Winding down?",
  night: "Good Night!",
  early_hours: "Still up? Get some rest",
};

function getGreetingBucket(hour: number): GreetingBucket {
  if (hour >= 5 && hour <= 7) return "early_morning";
  if (hour >= 8 && hour <= 11) return "morning";
  if (hour >= 12 && hour <= 13) return "lunch";
  if (hour >= 14 && hour <= 17) return "afternoon";
  if (hour >= 18 && hour <= 20) return "evening";
  if (hour >= 21 && hour <= 22) return "late_evening";
  if (hour === 23 || hour <= 1) return "night";
  return "early_hours";
}

export function ConversationGreeting({ className }: { className?: string }) {
  const { t } = useTranslation("page");
  const [hour, setHour] = React.useState(() => new Date().getHours());

  React.useEffect(() => {
    const updateHour = () => {
      setHour(new Date().getHours());
    };

    updateHour();
    const id = window.setInterval(updateHour, 60_000);
    return () => window.clearInterval(id);
  }, []);

  const bucket = getGreetingBucket(hour);

  return (
    <div className={cn("flex flex-col items-center gap-3 text-balance", className)}>
      <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/8 px-3.5 py-1.5 text-xs font-medium text-primary backdrop-blur-sm ring-1 ring-primary/10">
        <span className="relative flex h-2 w-2">
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
        </span>
        <span className="font-semibold">LastLab Playground</span>
        <span className="text-muted-foreground/40">•</span>
        <span className="text-muted-foreground/80 text-[10px] font-mono">relay-gw.pages.dev</span>
      </div>
      <span className="text-xl sm:text-2xl font-semibold tracking-tight text-foreground">
        {t(`conversations.greeting.${bucket}`, {
          defaultValue: GREETING_DEFAULTS[bucket],
        })}
      </span>
      <p className="text-xs text-muted-foreground/80 max-w-md text-center leading-relaxed">
        Mobile AI playground powered exclusively by the Relay Gateway (
        <span className="text-primary font-mono font-medium">@model-aggregator</span>).
      </p>
    </div>
  );
}
