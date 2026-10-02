import * as React from "react";

import type { TFunction } from "i18next";
import { Check, ChevronDown, Heart, LoaderCircle, Search } from "~/lib/material-icons";
import { useTranslation } from "react-i18next";

import { useCurrentAssistant } from "~/hooks/use-current-assistant";
import { getModelDisplayName } from "~/lib/display";
import { cn } from "~/lib/utils";
import api from "~/services/api";
import type { ModelAbility, ProviderModel } from "~/types";
import { AIIcon } from "~/components/ui/ai-icon";
import { Badge } from "~/components/ui/badge";
import { Button } from "~/components/ui/button";
import {
  Popover,
  PopoverContent,
  PopoverDescription,
  PopoverHeader,
  PopoverTitle,
  PopoverTrigger,
} from "~/components/ui/popover";
import { Input } from "~/components/ui/input";
import { ScrollArea } from "~/components/ui/scroll-area";

export interface ModelListProps {
  disabled?: boolean;
  className?: string;
  onChanged?: (model: ProviderModel) => void;
}

interface ModelSection {
  providerId: string;
  providerName: string;
  models: ProviderModel[];
}

const FAVORITE_SECTION_ID = "__favorites__";

function normalizeKeyword(value: string) {
  return value.trim().toLowerCase();
}

function formatModality(model: ProviderModel): string {
  const input = (model.inputModalities ?? []).join("+") || "TEXT";
  const output = (model.outputModalities ?? []).join("+") || "TEXT";
  return `${input} -> ${output}`;
}

function formatContextTokens(tokens?: number | null): string | null {
  if (!tokens || tokens <= 0) return null;
  if (tokens >= 1_000_000) return `${Math.round(tokens / 100_000) / 10}M ctx`;
  if (tokens >= 1_000) return `${Math.round(tokens / 1_000)}k ctx`;
  return `${tokens} ctx`;
}

function getAbilityLabel(ability: ModelAbility, t: TFunction): string {
  if (ability === "TOOL") {
    return t("model_list.ability_tool");
  }

  return t("model_list.ability_reasoning");
}

interface ModelOptionRowProps {
  model: ProviderModel;
  selected: boolean;
  updating: boolean;
  favorite: boolean;
  disabled: boolean;
  onSelect: (model: ProviderModel) => void | Promise<void>;
  onToggleFavorite: (model: ProviderModel) => void | Promise<void>;
  t: TFunction;
}

function ModelOptionRow({
  model,
  selected,
  updating,
  favorite,
  disabled,
  onSelect,
  onToggleFavorite,
  t,
}: ModelOptionRowProps) {
  const abilities = model.abilities ?? [];
  const ctxLabel = formatContextTokens(model.contextWindowTokens);

  return (
    <div
      role="button"
      tabIndex={disabled ? -1 : 0}
      aria-disabled={disabled}
      className={cn(
        "model-row flex w-full items-center gap-2 rounded-[var(--radius-card-inner)] border border-border/70 bg-background/90 px-2.5 py-1.5 text-left transition hover:bg-accent",
        disabled && "pointer-events-none opacity-60",
        selected && "border-primary/25 bg-primary/10",
      )}
      onClick={() => {
        if (disabled) {
          return;
        }

        void onSelect(model);
      }}
      onKeyDown={(event) => {
        if (disabled) {
          return;
        }

        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          void onSelect(model);
        }
      }}
    >
      <AIIcon 
        name={model.modelId} 
        size={24} 
        iconUrl={model.iconUrl}
        customIconUri={model.customIconUri}
        providerSlug={model.providerSlug}
      />

      <div className="min-w-0 flex-1">
        <div className="truncate text-xs font-medium leading-tight">
          {getModelDisplayName(model.displayName, model.modelId)}
        </div>
        <div className="text-muted-foreground truncate text-[11px] leading-tight font-mono">
          {model.modelId}
        </div>
        <div className="mt-0.5 flex flex-wrap gap-1">
          {ctxLabel && (
            <Badge variant="outline" className="px-1 py-0 text-[9px] font-mono border-cyan-500/40 text-cyan-400 bg-cyan-500/10">
              {ctxLabel}
            </Badge>
          )}
          <Badge variant="outline" className="px-1 py-0 text-[9px]">
            {formatModality(model)}
          </Badge>
          {abilities.map((ability) => (
            <Badge key={ability} variant="secondary" className={cn(
              "px-1 py-0 text-[9px]",
              ability === "REASONING" && "bg-purple-500/20 text-purple-300 border border-purple-500/30"
            )}>
              {getAbilityLabel(ability, t)}
            </Badge>
          ))}
          {model.providerSlug && (
            <Badge variant="outline" className="px-1 py-0 text-[9px] font-mono opacity-80">
              {model.providerSlug}
            </Badge>
          )}
        </div>
      </div>

      {updating ? (
        <LoaderCircle className="text-muted-foreground size-3.5 animate-spin" />
      ) : selected ? (
        <Check className="text-primary size-3.5" />
      ) : (
        <button
          type="button"
          className={favorite ? "text-primary" : "text-muted-foreground hover:text-primary"}
          onClick={(event) => {
            event.stopPropagation();
            void onToggleFavorite(model);
          }}
        >
          <Heart className={cn("size-3.5", favorite && "fill-current")} />
        </button>
      )}
    </div>
  );
}

export function ModelList({ disabled = false, className, onChanged }: ModelListProps) {
  const { t } = useTranslation("input");
  const { settings, currentAssistant } = useCurrentAssistant();

  const [open, setOpen] = React.useState(false);
  const [searchKeywords, setSearchKeywords] = React.useState("");
  const [selectedProviderId, setSelectedProviderId] = React.useState<string | null>(null);
  const [updatingModelId, setUpdatingModelId] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const currentModelId = currentAssistant?.chatModelId ?? settings?.chatModelId ?? null;
  const favoriteModelIds = settings?.favoriteModels ?? [];
  const favoriteModelIdSet = React.useMemo(() => new Set(favoriteModelIds), [favoriteModelIds]);

  const allModels = React.useMemo(() => {
    if (!settings) {
      return [];
    }

    return settings.providers
      .filter((provider) => provider.enabled)
      .flatMap((provider) => provider.models)
      .filter((model) => model.type === "CHAT");
  }, [settings]);

  const sections = React.useMemo<ModelSection[]>(() => {
    if (!settings) {
      return [];
    }

    const keyword = normalizeKeyword(searchKeywords);

    return settings.providers
      .filter((provider) => provider.enabled)
      .map((provider) => {
        const models = provider.models.filter((model) => {
          if (model.type !== "CHAT") {
            return false;
          }

          if (keyword.length === 0) {
            return true;
          }

          const displayName = getModelDisplayName(model.displayName, model.modelId).toLowerCase();
          const modelId = model.modelId.toLowerCase();
          return displayName.includes(keyword) || modelId.includes(keyword);
        });

        return {
          providerId: provider.id,
          providerName: provider.name,
          models,
        };
      })
      .filter((section) => section.models.length > 0);
  }, [searchKeywords, settings]);

  const selectedSection = React.useMemo(() => {
    if (sections.length === 0) {
      return null;
    }

    return sections.find((section) => section.providerId === selectedProviderId) ?? sections[0];
  }, [sections, selectedProviderId]);
  const filteredModels = React.useMemo(() => sections.flatMap((section) => section.models), [sections]);

  const [adminFilter, setAdminFilter] = React.useState<"all" | "reasoning" | "vision" | "highctx">("all");
  const [directModelInput, setDirectModelInput] = React.useState("");

  const favoriteModels = React.useMemo(() => {
    return favoriteModelIds
      .map((id) => filteredModels.find((model) => model.id === id))
      .filter((model): model is ProviderModel => model !== undefined);
  }, [favoriteModelIds, filteredModels]);
  const isFavoriteSectionSelected = selectedProviderId === FAVORITE_SECTION_ID;
  const rawDisplayedModels = isFavoriteSectionSelected ? favoriteModels : (selectedSection?.models ?? []);

  const displayedModels = React.useMemo(() => {
    return rawDisplayedModels.filter((model) => {
      if (adminFilter === "reasoning") {
        return (
          (model.abilities ?? []).includes("REASONING") ||
          /deepseek|r1|reason|opus|astra/i.test(model.modelId)
        );
      }
      if (adminFilter === "vision") {
        return (model.inputModalities ?? []).includes("IMAGE") || /image|vision/i.test(model.modelId);
      }
      if (adminFilter === "highctx") {
        return (model.contextWindowTokens ?? 0) >= 1_000_000 || /mimo|gemini|qwen/i.test(model.modelId);
      }
      return true;
    });
  }, [rawDisplayedModels, adminFilter]);

  const currentModel = React.useMemo(
    () => allModels.find((model) => model.id === currentModelId) ?? null,
    [allModels, currentModelId],
  );

  const currentModelLabel = currentModel
    ? getModelDisplayName(currentModel.displayName, currentModel.modelId)
    : t("model_list.select_model");

  React.useEffect(() => {
    if (!open) {
      setSearchKeywords("");
      setError(null);
    }
  }, [open]);

  React.useEffect(() => {
    if (!open) {
      return;
    }

    if (sections.length === 0 && favoriteModels.length === 0) {
      setSelectedProviderId(null);
      return;
    }

    if (selectedProviderId === FAVORITE_SECTION_ID && favoriteModels.length > 0) {
      return;
    }

    if (selectedProviderId && sections.some((section) => section.providerId === selectedProviderId)) {
      return;
    }

    const currentModelSection =
      currentModelId == null ? null : sections.find((section) => section.models.some((model) => model.id === currentModelId));
    setSelectedProviderId(currentModelSection?.providerId ?? (favoriteModels.length > 0 ? FAVORITE_SECTION_ID : sections[0]?.providerId ?? null));
  }, [currentModelId, favoriteModels.length, open, sections, selectedProviderId]);

  React.useEffect(() => {
    if (!disabled) {
      return;
    }

    setOpen(false);
  }, [disabled]);

  const handleSelectModel = React.useCallback(
    async (model: ProviderModel) => {
      if (disabled || !currentAssistant) {
        return;
      }

      if (model.id === currentModelId) {
        setOpen(false);
        return;
      }

      setUpdatingModelId(model.id);
      setError(null);

      try {
        await api.post<{ status: string }>("settings/assistant/model", {
          assistantId: currentAssistant.id,
          modelId: model.id,
        });
        onChanged?.(model);
        setOpen(false);
      } catch (changeError) {
        const message =
          changeError instanceof Error
            ? changeError.message
            : t("model_list.switch_model_failed");
        setError(message);
      } finally {
        setUpdatingModelId(null);
      }
    },
    [currentAssistant, currentModelId, disabled, onChanged, t],
  );

  const handleToggleFavorite = React.useCallback(
    async (model: ProviderModel) => {
      if (disabled || !settings) {
        return;
      }

      const isFavorite = favoriteModelIds.includes(model.id);
      const newFavoriteModels = isFavorite
        ? favoriteModelIds.filter((id) => id !== model.id)
        : [...favoriteModelIds, model.id];

      setUpdatingModelId(model.id);
      setError(null);

      try {
        await api.post<{ status: string }>("settings/favorite-models", {
          modelIds: newFavoriteModels,
        });
      } catch (changeError) {
        const message =
          changeError instanceof Error
            ? changeError.message
            : t("model_list.update_favorites_failed");
        setError(message);
      } finally {
        setUpdatingModelId(null);
      }
    },
    [disabled, favoriteModelIds, settings, t],
  );

  return (
    <Popover
      open={open}
      onOpenChange={(nextOpen) => {
        if (disabled || !currentAssistant) {
          setOpen(false);
          return;
        }

        setOpen(nextOpen);
      }}
    >
      <PopoverTrigger asChild>
        <Button
          id="model-trigger-btn"
          data-testid="model-trigger-btn"
          type="button"
          variant="ghost"
          size="sm"
          className={cn(
            "h-9 rounded-full border border-border/70 bg-muted/70 px-2.5 text-foreground shadow-none hover:bg-accent hover:text-accent-foreground sm:max-w-64 sm:justify-start sm:gap-2",
            className,
          )}
          disabled={disabled || !currentAssistant}
        >
          <AIIcon
            name={currentModel?.modelId ?? "auto"}
            size={16}
            className="bg-transparent"
            imageClassName="h-full w-full"
            iconUrl={currentModel?.iconUrl}
            customIconUri={currentModel?.customIconUri}
            providerSlug={currentModel?.providerSlug}
          />
          <span id="current-model-name" className="hidden min-w-0 flex-1 truncate text-left sm:block">
            {currentModelLabel}
          </span>
          <ChevronDown className="hidden size-3.5 shrink-0 sm:block" />
        </Button>
      </PopoverTrigger>

      <PopoverContent id="model-picker-modal" align="end" className="w-[min(96vw,30rem)] gap-0 p-0 overflow-hidden shadow-2xl border-border/80">
        <PopoverHeader className="px-4 pt-3.5 pb-2.5 border-b border-border/40 bg-muted/20">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <PopoverTitle className="text-sm font-semibold tracking-tight text-foreground">Admin Model Matrix</PopoverTitle>
              <span className="rounded-full bg-primary/15 px-2 py-0.5 text-[9px] font-semibold text-primary tracking-wide">
                RELAY ADMIN
              </span>
            </div>
            <span className="text-[10px] text-muted-foreground font-mono">relay-gw.pages.dev</span>
          </div>
          <PopoverDescription className="text-xs text-muted-foreground mt-0.5">
            Direct model routing powered exclusively by @model-aggregator
          </PopoverDescription>
        </PopoverHeader>

        <div className="space-y-2.5 px-3 py-2.5">
          <div className="relative">
            <Search className="text-muted-foreground pointer-events-none absolute top-1/2 left-2 size-3.5 -translate-y-1/2" />
            <Input
              id="model-search-input"
              value={searchKeywords}
              onChange={(event) => {
                setSearchKeywords(event.target.value);
              }}
              placeholder={t("model_list.search_placeholder")}
              className="h-8 border-border/70 bg-muted/45 pl-7 text-xs"
            />
          </div>

          <div id="model-quick-shelf" className="flex items-center gap-1 overflow-x-auto pb-0.5 scrollbar-none text-[11px]">
            <button
              type="button"
              onClick={() => setAdminFilter("all")}
              className={cn(
                "shelf-chip rounded-md px-2 py-1 transition text-xs",
                adminFilter === "all" ? "bg-primary text-primary-foreground font-medium" : "bg-muted text-muted-foreground hover:bg-accent"
              )}
            >
              All
            </button>
            <button
              type="button"
              onClick={() => setAdminFilter("reasoning")}
              className={cn(
                "shelf-chip rounded-md px-2 py-1 transition text-xs flex items-center gap-1",
                adminFilter === "reasoning" ? "bg-purple-600 text-white font-medium" : "bg-muted text-muted-foreground hover:bg-accent"
              )}
            >
              <span>🧠</span> Reasoning
            </button>
            <button
              type="button"
              onClick={() => setAdminFilter("vision")}
              className={cn(
                "shelf-chip rounded-md px-2 py-1 transition text-xs flex items-center gap-1",
                adminFilter === "vision" ? "bg-primary text-primary-foreground font-medium" : "bg-muted text-muted-foreground hover:bg-accent"
              )}
            >
              <span>👁️</span> Vision
            </button>
            <button
              type="button"
              onClick={() => setAdminFilter("highctx")}
              className={cn(
                "shelf-chip rounded-md px-2 py-1 transition text-xs flex items-center gap-1",
                adminFilter === "highctx" ? "bg-cyan-600 text-white font-medium" : "bg-muted text-muted-foreground hover:bg-accent"
              )}
            >
              <span>⚡</span> 1M+ Ctx
            </button>
          </div>

          <div className="flex items-center gap-1.5 pt-0.5">
            <Input
              id="direct-model-input"
              value={directModelInput}
              onChange={(e) => setDirectModelInput(e.target.value)}
              placeholder="Direct model ID (e.g. kimi-k3, gpt-6-astra)..."
              className="h-7 text-[11px] font-mono border-border/70 bg-muted/40"
              onKeyDown={(e) => {
                if (e.key === "Enter" && directModelInput.trim()) {
                  const target = allModels.find(m => m.modelId === directModelInput.trim()) || {
                    id: directModelInput.trim(),
                    modelId: directModelInput.trim(),
                    displayName: directModelInput.trim(),
                    type: "CHAT" as const,
                    inputModalities: ["TEXT"],
                    outputModalities: ["TEXT"],
                    abilities: [],
                  };
                  void handleSelectModel(target as any);
                  setDirectModelInput("");
                }
              }}
            />
            <Button
              id="direct-model-switch-btn"
              type="button"
              size="sm"
              variant="outline"
              className="h-7 px-2.5 text-[11px] font-mono shrink-0"
              disabled={!directModelInput.trim()}
              onClick={() => {
                const target = allModels.find(m => m.modelId === directModelInput.trim()) || {
                  id: directModelInput.trim(),
                  modelId: directModelInput.trim(),
                  displayName: directModelInput.trim(),
                  type: "CHAT" as const,
                  inputModalities: ["TEXT"],
                  outputModalities: ["TEXT"],
                  abilities: [],
                };
                void handleSelectModel(target as any);
                setDirectModelInput("");
              }}
            >
              Set
            </Button>
          </div>

          {error ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/10 px-2.5 py-1.5 text-[11px] text-destructive">
              {error}
            </div>
          ) : null}

          <div className="h-[24rem]">
            {sections.length === 0 && favoriteModels.length === 0 ? (
              <div className="rounded-[var(--radius-card)] border border-dashed border-border px-3 py-8 text-center text-sm text-muted-foreground">
                {t("model_list.empty")}
              </div>
            ) : (
              <div className="flex h-full min-h-0 flex-col gap-3">
                <div className="overflow-hidden rounded-[var(--radius-card)] border border-border/70 bg-muted/45 p-2">
                  <ScrollArea className="max-h-20 w-full">
                    <div className="flex flex-wrap items-center gap-1.5 pb-1">
                      {favoriteModels.length > 0 && (
                        <button
                          type="button"
                          className={cn(
                            "inline-flex items-center gap-1 rounded-full border border-border/70 bg-background px-2.5 py-1 text-xs transition hover:bg-accent",
                            isFavoriteSectionSelected && "border-primary/25 bg-primary/10 text-primary",
                          )}
                          onClick={() => {
                            setSelectedProviderId(FAVORITE_SECTION_ID);
                          }}
                        >
                          <Heart className={cn("size-3", isFavoriteSectionSelected && "fill-current")} />
                          <span>{t("model_list.favorites")}</span>
                        </button>
                      )}

                      {sections.map((section) => {
                        const selected = section.providerId === selectedProviderId;
                        return (
                          <button
                            key={section.providerId}
                            type="button"
                            className={cn(
                              "inline-flex items-center gap-1 rounded-full border border-border/70 bg-background px-2.5 py-1 text-xs transition hover:bg-accent",
                              selected && "border-primary/25 bg-primary/10 text-primary",
                            )}
                            onClick={() => {
                              setSelectedProviderId(section.providerId);
                            }}
                          >
                            <AIIcon
                              name={section.providerName}
                              size={12}
                              className="bg-transparent"
                              imageClassName="h-full w-full"
                              providerSlug={section.providerName}
                            />
                            <span className="truncate">{section.providerName}</span>
                          </button>
                        );
                      })}
                    </div>
                  </ScrollArea>
                </div>

                <div className="min-h-0 flex-1 overflow-hidden rounded-[var(--radius-card)] border border-border/70 bg-muted/30 p-2">
                  <ScrollArea className="h-full min-h-0">
                    <div id="modal-models-list" className="space-y-1">
                      {displayedModels.map((model) => (
                        <ModelOptionRow
                          key={model.id}
                          model={model}
                          selected={model.id === currentModelId}
                          updating={model.id === updatingModelId}
                          favorite={favoriteModelIdSet.has(model.id)}
                          disabled={disabled || updatingModelId !== null}
                          onSelect={handleSelectModel}
                          onToggleFavorite={handleToggleFavorite}
                          t={t}
                        />
                      ))}
                    </div>
                  </ScrollArea>
                </div>
              </div>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
