package com.toolhub.session;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Date;

public class ToolHubSessionJwtProvider {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    private static final String SECRET = dotenv.get("TOOLHUB_SESSION_SECRET", "");

    private static final Algorithm ALGORITHM = Algorithm.HMAC256(SECRET);

    private static final String ISSUER = "toolhub";

    private static final long SESSION_TTL_MS = 3L * 24 * 60 * 60 * 1000; // 3 days

    public static String generateSessionToken(
            String userId,
            String role,
            String email) {

        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + SESSION_TTL_MS);

        return JWT.create()
                .withIssuer(ISSUER)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .withSubject(userId)
                .withClaim("role", role)
                .withClaim("email", email)
                .sign(ALGORITHM);
    }

    public static DecodedJWT verifySessionToken(String token) {
        JWTVerifier verifier = JWT.require(ALGORITHM)
                .withIssuer(ISSUER)
                .build();

        return verifier.verify(token);
    }
}
