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
      <div style={styles.card}>
        <img src="/tool_hub-logo.png" alt="ToolHub" style={styles.logo} />

        <h1 style={styles.title}>ToolHub</h1>
        <p style={styles.subtitle}>Secure access to your self-hosted tools</p>

        <button onClick={signInWithGoogle} style={styles.googleButton}>
          <img
            src="https://developers.google.com/identity/images/g-logo.png"
            alt=""
            style={styles.googleIcon}
          />
          Continue with Google
        </button>

        <p style={styles.footer}>Powered by ToolHub SSO</p>
      </div>
    </div>
  );
}
