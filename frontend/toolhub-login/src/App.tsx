import { ClerkProvider, SignedIn, SignedOut } from "@clerk/clerk-react";
import RedirectBack from "./RedirectBack";
import LoginScreen from "./LoginScreen";
import { useEffect, useState } from "react";
import { styles } from "./LoginScreen.styles";

const CLERK_PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISH_KEY;
const REDIRECT_KEY = "sso_redirect_origin";

export default function App() {
  const [isValidEntry, setIsValidEntry] = useState<boolean | null>(null);

  useEffect(() => {
  // 1️⃣ If we already have redirect intent, trust it
  const existing = sessionStorage.getItem(REDIRECT_KEY);
  if (existing) {
    setIsValidEntry(true);
    return;
  }

  // 2️⃣ Otherwise, try extracting from URL
  const params = new URLSearchParams(window.location.search);
  const redirectParam = params.get("redirect");

  if (redirectParam) {
    try {
      const url = new URL(redirectParam);

      // 🔒 Normalize protocol
      if (url.hostname !== "localhost") {
        url.protocol = "https:";
      }

      sessionStorage.setItem(REDIRECT_KEY, url.origin);
      setIsValidEntry(true);
    } catch {
      setIsValidEntry(false);
    }
  } else {
    // 3️⃣ Truly lost (no stored intent, no URL intent)
    setIsValidEntry(false);
  }
}, []);


  if (!CLERK_PUBLISHABLE_KEY) {
    return (
      <AccessState
        title="Clerk misconfigured"
        message="Authentication cannot start because the publishable key is missing."
      />
    );
  }

  // Prevent flicker while resolving validity
  if (isValidEntry === null) {
    return <AccessState title="Checking access" message="Preparing your secure sign-in." />;
  }

  if (!isValidEntry) {
    return (
      <AccessState
        title="Start from a tool"
        message="Open ToolHub from one of your tools so SSO knows where to send you next."
      />
    );
  }

  return (
    <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY}>
      <SignedOut>
        <LoginScreen />
      </SignedOut>

      <SignedIn>
        <RedirectBack />
      </SignedIn>
    </ClerkProvider>
  );
}

function AccessState({ title, message }: { title: string; message: string }) {
  return (
    <div style={styles.container}>
      <div style={styles.ambient} />
      <section style={styles.statusCard}>
        <img src="/tool_hub_logo_dark.png" alt="ToolHub" style={styles.statusLogo} />
        <h1 style={styles.statusTitle}>{title}</h1>
        <p style={styles.statusMessage}>{message}</p>
      </section>
    </div>
  );
}
