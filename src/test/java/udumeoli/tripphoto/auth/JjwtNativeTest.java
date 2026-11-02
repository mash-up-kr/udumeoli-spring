package udumeoli.tripphoto.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** JVM과 nativeTest에서 Apple 로그인과 동일한 JJWT 파싱 경로를 실행한다. */
class JjwtNativeTest {
    @Test
    void parseAppleStyleSignedToken() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        var parser = Jwts.parser()
                .verifyWith((RSAPublicKey) keys.getPublic())
                .requireIssuer("https://appleid.apple.com")
                .requireAudience("test-apple-client")
                .build();

        String token = token(keys, "test-apple-client");
        var claims = parser.parseSignedClaims(token).getPayload();
        assertEquals("test-user", claims.getSubject());
        assertEquals("test@example.com", claims.get("email"));
        assertThrows(JwtException.class, () -> parser.parseSignedClaims(token(keys, "untrusted-client")));
        KeyPair otherKeys = generator.generateKeyPair();
        assertThrows(JwtException.class, () -> parser.parseSignedClaims(token(otherKeys, "test-apple-client")));
    }

    private String token(KeyPair keys, String audience) throws JOSEException {
        Instant now = Instant.now();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                .issuer("https://appleid.apple.com")
                .subject("test-user")
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("email", "test@example.com")
                .build());
        token.sign(new RSASSASigner(keys.getPrivate()));
        return token.serialize();
    }
}
