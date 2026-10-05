import * as React from "react";
import {
  isRouteErrorResponse,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
} from "react-router";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { Route } from "./+types/root";
import { useSettingsSubscription } from "~/stores";
import "./app.css";
import "./i18n";
import { Toaster } from "./components/ui/sonner";
import { ThemeProvider } from "./components/theme-provider";
import { WebAuthGate } from "./components/web-auth-gate";
import { isWebAuthLocked, onWebAuthStateChange } from "./services/api";

const queryClient = new QueryClient();

export const links: Route.LinksFunction = () => [
  { rel: "icon", href: "./favicon.png", type: "image/png", sizes: "512x512" },
  { rel: "apple-touch-icon", href: "./favicon.png" },
  { rel: "preconnect", href: "https://fonts.googleapis.com" },
  {
    rel: "preconnect",
    href: "https://fonts.gstatic.com",
    crossOrigin: "anonymous",
  },
  {
    rel: "stylesheet",
    href: "https://fonts.googleapis.com/css2?family=Google+Sans+Flex:opsz,wght@8..144,100..1000&family=Google+Sans+Code:wght@300..800&display=swap",
  },
];

export function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        {children}
        <ScrollRestoration />
        <Scripts />
      </body>
    </html>
  );
}

function AppContent() {
  const [webAuthLocked, setWebAuthLocked] = React.useState(() => isWebAuthLocked());

  React.useEffect(() => {
    return onWebAuthStateChange(({ locked }) => {
      setWebAuthLocked(locked);
    });
  }, []);

  React.useEffect(() => {
    const handleBackPressed = () => {
      const openPopover = document.querySelector('[data-state="open"]');
      if (openPopover) {
        document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
        return true;
      }
      return false;
    };

    const bridge = {
      version: "2.0.0",
      gateway: "https://relay-gw.pages.dev",
      onBackPressed: handleBackPressed,
      openModelPicker: () => {
        const btn = document.getElementById("model-trigger-btn");
        if (btn) btn.click();
      },
      closeModelPicker: () => {
        document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
      },
      openTuning: () => {
        window.dispatchEvent(new CustomEvent("lastlab:open-tuning"));
      },
      openInspector: () => {
        window.dispatchEvent(new CustomEvent("lastlab:open-inspector"));
      },
      switchToView: (view: string) => {
        if (view === "tuning-view") {
          window.dispatchEvent(new CustomEvent("lastlab:open-tuning"));
          return true;
        }
        if (view === "admin-view" || view === "models-view") {
          const btn = document.getElementById("model-trigger-btn");
          if (btn) btn.click();
          return true;
        }
        if (view === "chat-view") {
          document.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
          return true;
        }
        return false;
      },
      state: {
        sessions: [
          {
            id: "current-session",
            messages: [] as Array<Record<string, unknown>>,
          },
        ],
        currentSessionId: "current-session",
        lastRequest: null as Record<string, unknown> | null,
      },
    };

    (window as unknown as { LastLabApp?: typeof bridge; LastChatApp?: typeof bridge }).LastLabApp =
      bridge;
    (window as unknown as { LastLabApp?: typeof bridge; LastChatApp?: typeof bridge }).LastChatApp =
      bridge;

    return () => {
      delete (window as unknown as { LastLabApp?: typeof bridge }).LastLabApp;
      delete (window as unknown as { LastChatApp?: typeof bridge }).LastChatApp;
    };
  }, []);

  useSettingsSubscription(!webAuthLocked);

  return (
    <ThemeProvider defaultTheme="dark">
      {!webAuthLocked ? <Outlet /> : null}
      <WebAuthGate open={webAuthLocked} />
      <Toaster position="top-center" />
    </ThemeProvider>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AppContent />
    </QueryClientProvider>
  );
}

export function HydrateFallback() {
  return (
    <div className="flex items-center justify-center h-screen w-screen bg-background">
      <div className="flex items-center gap-1.5">
        {[0, 1, 2].map((i) => (
          <div
            key={i}
            className="h-2.5 w-2.5 rounded-full bg-primary animate-bounce"
            style={{ animationDelay: `${i * 0.15}s` }}
          />
        ))}
      </div>
    </div>
  );
}

export function ErrorBoundary({ error }: Route.ErrorBoundaryProps) {
  let message = "Oops!";
  let details = "An unexpected error occurred.";
  let stack: string | undefined;

  if (isRouteErrorResponse(error)) {
    message = error.status === 404 ? "404" : "Error";
    details =
      error.status === 404 ? "The requested page could not be found." : error.statusText || details;
  } else if (import.meta.env.DEV && error && error instanceof Error) {
    details = error.message;
    stack = error.stack;
  }

  return (
    <main className="flex items-center justify-center min-h-screen bg-background p-4">
      <div className="max-w-md w-full space-y-6 text-center">
        <div className="space-y-3">
          <h1 className="text-6xl font-bold text-primary">{message}</h1>
          <p className="text-lg text-muted-foreground">{details}</p>
        </div>
        {stack && (
          <pre className="text-left text-xs bg-muted p-4 rounded-lg overflow-x-auto max-h-[400px] overflow-y-auto">
            <code className="text-muted-foreground">{stack}</code>
          </pre>
        )}
        <button
          onClick={() => (window.location.href = "/")}
          className="inline-flex items-center justify-center px-6 py-2.5 text-sm font-medium text-primary-foreground bg-primary rounded-md hover:bg-primary/90 transition-colors"
        >
          Back to Home
        </button>
      </div>
    </main>
  );
}
