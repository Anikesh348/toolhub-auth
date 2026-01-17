import { useEffect } from "react";
import { useAuth } from "@clerk/clerk-react";

const REDIRECT_KEY = "sso_redirect_origin";

export default function RedirectBack() {
  const { getToken, isLoaded, isSignedIn } = useAuth();

  useEffect(() => {
    if (!isLoaded || !isSignedIn) return;

    (async () => {
      try {
        const clerkJwt = await getToken();
        if (!clerkJwt) return;

        const origin = sessionStorage.getItem(REDIRECT_KEY);

        if (!origin) {
          console.error("[SSO] Missing redirect origin");
          return;
        }

        // Clear immediately to prevent reuse
        sessionStorage.removeItem(REDIRECT_KEY);

        const targetUrl = new URL(origin);
        targetUrl.searchParams.set("handoff_jwt", clerkJwt);

        console.info("[SSO] Final redirect:", targetUrl.toString());
        window.location.replace(targetUrl.toString());
      } catch (err) {
        console.error("[SSO] RedirectBack failed", err);
      }
    })();
  }, [isLoaded, isSignedIn, getToken]);

  return <p>Signing you in…</p>;
}
