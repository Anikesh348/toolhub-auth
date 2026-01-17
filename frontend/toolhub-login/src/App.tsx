import { ClerkProvider, SignedIn, SignedOut } from "@clerk/clerk-react";
import RedirectBack from "./RedirectBack";
import LoginScreen from "./LoginScreen";
import { useEffect, useState } from "react";

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
    return <div>Clerk misconfigured</div>;
  }

  // Prevent flicker while resolving validity
  if (isValidEntry === null) {
    return <div>Loading…</div>;
  }

  if (!isValidEntry) {
    return <div>You seem to be lost. Please access this page via a tool.</div>;
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
