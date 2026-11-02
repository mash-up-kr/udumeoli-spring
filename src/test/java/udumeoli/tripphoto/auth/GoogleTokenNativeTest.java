package udumeoli.tripphoto.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.json.gson.GsonFactory;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Google 로그인에서 사용하는 JSON 파싱과 서명 검증을 외부 요청 없이 실행한다. */
class GoogleTokenNativeTest {
    @Test
    void parseGoogleSignedToken() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        Instant now = Instant.now();
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience("test-google-client")
                .subject("test-user")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("email", "test@example.com")
                .claim("email_verified", true)
                .claim("nonce", "test-nonce")
                .claim("azp", "test-google-client")
                .claim("picture", "https://example.com/profile.png")
                .build());
        signed.sign(new RSASSASigner(keys.getPrivate()));

        GoogleIdToken token = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), signed.serialize());
        assertEquals("RS256", token.getHeader().getAlgorithm());
        assertEquals("test-user", token.getPayload().getSubject());
        assertEquals("test@example.com", token.getPayload().getEmail());
        assertEquals(Boolean.TRUE, token.getPayload().getEmailVerified());
        assertEquals("test-nonce", token.getPayload().getNonce());
        assertEquals("test-google-client", token.getPayload().getAuthorizedParty());
        assertEquals("https://example.com/profile.png", token.getPayload().get("picture"));
        assertTrue(token.verifyAudience(java.util.List.of("test-google-client")));
        assertTrue(token.verifySignature(keys.getPublic()));
    }
}
