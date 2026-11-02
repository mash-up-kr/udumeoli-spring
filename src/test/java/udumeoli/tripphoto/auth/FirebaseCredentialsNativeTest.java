package udumeoli.tripphoto.auth;

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 테스트용 키와 HTTP 응답으로 Firebase 서비스 계정 인증을 오프라인 검증한다. */
class FirebaseCredentialsNativeTest {
    @Test
    void refreshServiceAccountToken() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keys = generator.generateKeyPair();
        String privateKey = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        var jsonFactory = GsonFactory.getDefaultInstance();
        String json = jsonFactory.toString(Map.of(
                "type", "service_account",
                "client_id", "test-client",
                "client_email", "test@test-project.iam.gserviceaccount.com",
                "private_key_id", "test-key",
                "private_key", privateKey,
                "token_uri", "https://oauth2.googleapis.com/token"));
        var transport = new MockHttpTransport.Builder()
                .setLowLevelHttpResponse(new MockLowLevelHttpResponse()
                        .setContentType("application/json")
                        .setContent("{\"access_token\":\"offline-token\",\"expires_in\":3600,\"token_type\":\"Bearer\"}"))
                .build();
        var credentials = ServiceAccountCredentials.fromStream(
                        new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), () -> transport)
                .createScoped(List.of("https://www.googleapis.com/auth/firebase.remoteconfig"));

        assertEquals("offline-token", credentials.refreshAccessToken().getTokenValue());
        String body = transport.getLowLevelHttpRequest().getContentAsString();
        String assertion = java.util.Arrays.stream(body.split("&"))
                .filter(parameter -> parameter.startsWith("assertion="))
                .map(parameter -> URLDecoder.decode(parameter.substring("assertion=".length()), StandardCharsets.UTF_8))
                .findFirst().orElseThrow();
        var jwt = JsonWebSignature.parse(jsonFactory, assertion);
        assertEquals("test-key", jwt.getHeader().getKeyId());
        assertEquals("test@test-project.iam.gserviceaccount.com", jwt.getPayload().getIssuer());
        assertEquals("https://www.googleapis.com/auth/firebase.remoteconfig", jwt.getPayload().get("scope"));
        assertTrue(jwt.verifySignature(keys.getPublic()));
    }
}
