import { useEffect } from "react";

export default function RedirectBack() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const redirect = params.get("redirect") || "http://localhost:8082";

    window.location.href = redirect;
  }, []);

  return <p>Signing you in…</p>;
}
