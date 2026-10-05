import * as React from "react";
import { useTranslation } from "react-i18next";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "~/components/ui/dialog";
import { Button } from "~/components/ui/button";
import { Badge } from "~/components/ui/badge";
import { Input } from "~/components/ui/input";
import { toast } from "sonner";
import { InfoIcon, Key, Visibility } from "~/lib/material-icons";

/** What `GET /api/gateway-key` returns. */
export interface GatewayKeyState {
  configured: boolean;
  masked: string | null;
}

/**
 * Reads and invalidates the stored key state.
 *
 * <p>The backend is the single source of truth: the key is persisted there and never mirrored
 * into browser storage, so every read goes back to the API.
 */
export function useGatewayKey(): {
  state: GatewayKeyState | null;
  reload: () => Promise<void>;
} {
  const [state, setState] = React.useState<GatewayKeyState | null>(null);

  const reload = React.useCallback(async () => {
    try {
      const response = await fetch("/api/gateway-key", { cache: "no-store" });
      if (!response.ok) return;
      setState((await response.json()) as GatewayKeyState);
    } catch {
      // The backend is unreachable. Leave the state unknown rather than assuming a key exists.
    }
  }, []);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  return { state, reload };
}

interface GatewayKeyDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  state: GatewayKeyState | null;
  /** Called after the backend's stored key changed, so callers can re-read dependent state. */
  onChanged: () => void;
}

export function GatewayKeyDialog({
  open,
  onOpenChange,
  state,
  onChanged,
}: GatewayKeyDialogProps) {
  const { t } = useTranslation("common");
  const [draft, setDraft] = React.useState("");
  const [revealed, setRevealed] = React.useState(false);
  const [busy, setBusy] = React.useState(false);

  // Never keep a typed key in memory after the dialog closes.
  React.useEffect(() => {
    if (!open) {
      setDraft("");
      setRevealed(false);
    }
  }, [open]);

  const configured = state?.configured === true;
  const dirty = draft.trim().length > 0;

  const handleSave = async () => {
    if (!dirty || busy) return;
    setBusy(true);
    try {
      const response = await fetch("/api/gateway-key", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ key: draft.trim() }),
      });
      const data = (await response.json()) as { error?: string; masked?: string | null };
      if (!response.ok) {
        throw new Error(data.error || `Request failed (${response.status})`);
      }
      setDraft("");
      setRevealed(false);
      toast.success(
        data.masked
          ? t("gateway_key_dialog.saved_masked", { masked: data.masked })
          : t("gateway_key_dialog.saved"),
      );
      onChanged();
      onOpenChange(false);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : t("gateway_key_dialog.save_failed"),
      );
    } finally {
      setBusy(false);
    }
  };

  const handleClear = async () => {
    if (busy) return;
    setBusy(true);
    try {
      const response = await fetch("/api/gateway-key", { method: "DELETE" });
      const data = (await response.json()) as { error?: string };
      if (!response.ok) {
        throw new Error(data.error || `Request failed (${response.status})`);
      }
      setDraft("");
      toast.success(t("gateway_key_dialog.cleared"));
      onChanged();
      onOpenChange(false);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : t("gateway_key_dialog.clear_failed"),
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Key className="size-4" />
            {t("gateway_key_dialog.title")}
          </DialogTitle>
          <DialogDescription>{t("gateway_key_dialog.description")}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-muted/30 px-3 py-2.5">
            <span className="text-xs text-muted-foreground">
              {t("gateway_key_dialog.status")}
            </span>
            <Badge
              variant="outline"
              className={
                configured
                  ? "border-emerald-500/40 text-emerald-400 bg-emerald-500/10"
                  : "border-amber-500/40 text-amber-400 bg-amber-500/10"
              }
            >
              {configured
                ? state?.masked || t("gateway_key_dialog.configured")
                : t("gateway_key_dialog.not_configured")}
            </Badge>
          </div>

          <div className="space-y-2">
            <label
              htmlFor="gateway-key-input"
              className="text-xs font-medium text-foreground"
            >
              {t("gateway_key_dialog.new_key")}
            </label>
            <div className="relative">
              <Input
                id="gateway-key-input"
                data-testid="gateway-key-input"
                type={revealed ? "text" : "password"}
                autoComplete="off"
                spellCheck={false}
                placeholder={configured ? "••••••••••" : "sk-..."}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" && dirty) void handleSave();
                }}
                className="pr-9 font-mono text-xs"
              />
              <button
                type="button"
                onClick={() => setRevealed((value) => !value)}
                aria-label={
                  revealed ? t("gateway_key_dialog.hide") : t("gateway_key_dialog.show")
                }
                title={revealed ? t("gateway_key_dialog.hide") : t("gateway_key_dialog.show")}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground transition hover:text-foreground"
              >
                <Visibility className={revealed ? "size-3.5" : "size-3.5 opacity-45"} />
              </button>
            </div>
            <p className="flex items-start gap-1.5 text-[11px] text-muted-foreground/80">
              <InfoIcon className="mt-px size-3 shrink-0" />
              <span>{t("gateway_key_dialog.storage_hint")}</span>
            </p>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:justify-between">
          <Button
            variant="outline"
            size="sm"
            onClick={() => void handleClear()}
            disabled={!configured || busy}
          >
            {t("gateway_key_dialog.clear")}
          </Button>
          <div className="flex gap-2">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => onOpenChange(false)}
              disabled={busy}
            >
              {t("gateway_key_dialog.cancel")}
            </Button>
            <Button
              size="sm"
              onClick={() => void handleSave()}
              disabled={!dirty || busy}
            >
              {busy ? t("gateway_key_dialog.saving") : t("gateway_key_dialog.save")}
            </Button>
          </div>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}