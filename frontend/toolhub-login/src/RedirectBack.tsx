import { useEffect } from "react";
import { useAuth } from "@clerk/clerk-react";

const DEFAULT_REDIRECT = "https://hostingfrompurva.xyz";

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

        let targetUrl: URL;

        if (redirectParam) {
          try {
            targetUrl = new URL(redirectParam);
            console.info(
              "[SSO] Redirecting to provided URL:",
              targetUrl.toString()
            );
          } catch (err) {
            console.error("[SSO] Invalid redirect URL, falling back", err);
            targetUrl = new URL(DEFAULT_REDIRECT);
          }
        } else {
          targetUrl = new URL(DEFAULT_REDIRECT);
          console.info(
            "[SSO] No redirect param found, using default:",
            targetUrl.toString()
          );
        }

        // 🔐 Append one-time handoff JWT
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
