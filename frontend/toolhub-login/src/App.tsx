import { ClerkProvider, SignedIn, SignedOut, SignIn } from "@clerk/clerk-react";
import { useEffect } from "react";
import RedirectBack from "./RedirectBack";

const CLERK_PUBLISHABLE_KEY = "pk_live_Y2xlcmsuaG9zdGluZ2Zyb21wdXJ2YS54eXok";

export default function App() {
  useEffect(() => {
    console.info("[SSO] App mounted");
    console.info(
      "[SSO] Using publishable key:",
      CLERK_PUBLISHABLE_KEY?.slice(0, 12) + "..."
    );
  }, []);

  if (!CLERK_PUBLISHABLE_KEY) {
    console.error("[SSO] Missing Clerk publishable key");
    return <div>Clerk misconfigured</div>;
  }

  return (
    <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY}>
      <SignedOut>
        <SignIn routing="virtual" />
      </SignedOut>

      <SignedIn>
        <RedirectBack />
      </SignedIn>
    </ClerkProvider>
  );
}
