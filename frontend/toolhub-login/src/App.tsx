import { ClerkProvider, SignedIn, SignedOut } from "@clerk/clerk-react";
import RedirectBack from "./RedirectBack";
import LoginScreen from "./LoginScreen";

const CLERK_PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISH_KEY;

export default function App() {
  if (!CLERK_PUBLISHABLE_KEY) {
    return <div>Clerk misconfigured</div>;
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
