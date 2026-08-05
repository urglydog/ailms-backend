package com.lms.auth.provider;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Google OAuth2 token verification provider.
 * Verifies Google ID tokens and extracts user information.
 */
@Component
@RequiredArgsConstructor
public class GoogleOAuthProvider {

    @Value("${google.oauth.client-id}")
    private String clientId;

    /**
     * Verify Google ID token and return payload containing user information.
     *
     * @param idToken Google ID token from frontend
     * @return GoogleIdToken.Payload containing email, name, picture, etc.
     * @throws GeneralSecurityException if verification fails due to security error
     * @throws IOException if verification fails due to I/O error
     * @throws RuntimeException if token is invalid
     */
    public GoogleIdToken.Payload verifyToken(String idToken) throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
            new NetHttpTransport(), new GsonFactory())
            .setAudience(Collections.singletonList(clientId))
            .build();

        GoogleIdToken token = verifier.verify(idToken);
        if (token != null) {
            return token.getPayload();
        }
        throw new RuntimeException("Invalid Google ID token");
    }
}
