import { useSignIn } from "@clerk/clerk-react";
import { styles } from "./LoginScreen.styles";

export default function LoginScreen() {
  const { signIn, isLoaded } = useSignIn();

  const signInWithGoogle = async () => {
    if (!isLoaded) return;

    await signIn.authenticateWithRedirect({
      strategy: "oauth_google",
      redirectUrl: "/",
      redirectUrlComplete: "/",
    });
  };

  return (
    <div style={styles.container}>
      <div style={styles.ambient} />
      <main style={styles.shell}>
        <section style={styles.brandPanel} aria-label="ToolHub secure sign in">
          <div style={styles.brandTopline}>ToolHub SSO</div>
          <h1 style={styles.heroTitle}>One secure entry point for every tool.</h1>
          <p style={styles.heroCopy}>
            Continue with your approved Google account to reach your ToolHub
            workspace.
          </p>
        </section>

      <div style={styles.card}>
          <img src="/tool_hub_logo_dark.png" alt="ToolHub" style={styles.logo} />

          <div style={styles.cardHeader}>
            <p style={styles.kicker}>Authentication</p>
            <h2 style={styles.title}>Welcome back</h2>
            <p style={styles.subtitle}>Sign in to continue to ToolHub.</p>
          </div>

          <button onClick={signInWithGoogle} style={styles.googleButton}>
            <img
              src="https://developers.google.com/identity/images/g-logo.png"
              alt=""
              style={styles.googleIcon}
            />
            Continue with Google
          </button>

          <p style={styles.footer}>Protected by ToolHub SSO</p>
      </div>
      </main>
    </div>
  );
}
