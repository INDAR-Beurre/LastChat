import * as React from "react";
import { ChevronDown, ChevronRight } from "~/lib/material-icons";
import Markdown from "~/components/markdown/markdown";
import type { DisplaySetting } from "~/types";
import Think from "~/assets/think.svg?react";
import { cn } from "~/lib/utils";

interface ReasoningPartProps {
  reasoning: string;
  displaySetting?: DisplaySetting | null;
  isFinished?: boolean;
}

export function ReasoningPart({
  reasoning,
  displaySetting,
  isFinished = true,
}: ReasoningPartProps) {
  const [expanded, setExpanded] = React.useState(false);

  if (!reasoning) return null;

  return (
    <div className="rounded-xl border border-border/60 bg-muted/20 backdrop-blur-sm transition-colors">
      <button
        type="button"
        className="flex w-full items-center gap-2 px-3 py-2 text-left text-xs font-medium text-muted-foreground transition hover:text-foreground"
        onClick={() => setExpanded(!expanded)}
      >
        {expanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
        <Think className={cn("h-3.5 w-3.5 text-purple-400", !isFinished && "animate-pulse")} />
        <span>{isFinished ? "Thinking Process" : "Thinking..."}</span>
        {!isFinished && (
          <span className="relative flex h-1.5 w-1.5">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-purple-400 opacity-75" />
            <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-purple-500" />
          </span>
        )}
      </button>
      {expanded && (
        <div className="border-t border-border/40 px-3 py-2 text-xs text-muted-foreground">
          <Markdown content={reasoning} displaySetting={displaySetting} />
        </div>
      )}
    </div>
  );
}
