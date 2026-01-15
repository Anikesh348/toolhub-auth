import { ClerkProvider, SignedIn, SignedOut, SignIn } from "@clerk/clerk-react";
import RedirectBack from "./RedirectBack.tsx";

const CLERK_PUBLISHABLE_KEY =
  "pk_test_ZWFzeS1tYXJ0aW4tMjUuY2xlcmsuYWNjb3VudHMuZGV2JA";

export default function App() {
  return (
    <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY}>
      <SignedOut>
        <SignIn
          routing="virtual"
          appearance={{
            elements: {
              card: { boxShadow: "none" },
            },
          }}
        />
      </SignedOut>

      <SignedIn>
        <RedirectBack />
      </SignedIn>
    </ClerkProvider>
  );
}
