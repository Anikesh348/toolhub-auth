import { useEffect } from "react";

const ALLOWED_HOSTS = [
  "logs.hostingfrompurva.xyz",
  "metrics.hostingfrompurva.xyz",
  "localhost:8082",
];

export default function RedirectBack() {
  useEffect(() => {
    console.info("[SSO] RedirectBack mounted");

    const params = new URLSearchParams(window.location.search);
    const redirectParam = params.get("redirect");

    let targetUrl: string | null = null;

    try {
      if (redirectParam) {
        const url = new URL(redirectParam);

        if (ALLOWED_HOSTS.includes(url.host)) {
          targetUrl = url.toString();
          console.info("[SSO] Valid redirect target:", targetUrl);
        } else {
          console.error("[SSO] Blocked redirect to untrusted host:", url.host);
        }
      }
    } catch (err) {
      console.error("[SSO] Invalid redirect URL", err);
    }

    // Fallback (safe default)
    if (!targetUrl) {
      targetUrl =
        window.location.hostname === "localhost"
          ? "http://localhost:8082"
          : "https://logs.hostingfrompurva.xyz";

      console.info("[SSO] Using fallback redirect:", targetUrl);
    }

    window.location.replace(targetUrl);
  }, []);

  return <p>Signing you in…</p>;
}
