import { useEffect } from "react";
import { useAuth } from "@clerk/clerk-react";

export default function RedirectBack() {
  const { getToken, isLoaded, isSignedIn } = useAuth();

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return;

    console.info("[SSO] RedirectBack mounted & signed in");

    (async () => {
      try {
        const clerkJwt = await getToken();

        if (!clerkJwt) {
          console.error("[SSO] Failed to obtain Clerk JWT");
          return;
        }

        const params = new URLSearchParams(window.location.search);
        const redirectParam = params.get("redirect");

        let targetUrl: URL | null = null;

        if (redirectParam) {
          try {
            const url = new URL(redirectParam);
            targetUrl = url;
          } catch (err) {
            console.error("[SSO] Invalid redirect URL", err);
          }
        }

        // Safe fallback
        if (!targetUrl) {
          targetUrl =
            window.location.hostname === "localhost"
              ? new URL("http://localhost:8082")
              : new URL("https://hostingfrompurva.xyz");

          console.info("[SSO] Using fallback redirect:", targetUrl.toString());
        }

        // 🔐 Append JWT as one-time handoff token
        targetUrl.searchParams.set("handoff_jwt", clerkJwt);

        console.info("[SSO] Redirecting with handoff JWT");

        window.location.replace(targetUrl.toString());
      } catch (err) {
        console.error("[SSO] RedirectBack failed", err);
      }
    })();
  }, [isLoaded, isSignedIn, getToken]);

  return <p>Signing you in…</p>;
}
