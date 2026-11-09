package com.lms.auth.provider;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
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

    @Value("${google.oauth.client-secret}")
    private String clientSecret;

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
        NetHttpTransport transport = new NetHttpTransport.Builder().doNotValidateCertificate().build();
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
            transport, new GsonFactory())
            .setAudience(Collections.singletonList(clientId))
            .build();

        GoogleIdToken token = verifier.verify(idToken);
        if (token != null) {
            return token.getPayload();
        }
        throw new RuntimeException("Invalid Google ID token");
    }

    /**
     * Đổi Authorization Code (luồng mobile — app không giữ client secret) lấy id_token thật
     * bằng cách gọi thẳng Google token endpoint từ server. Chỉ BE mới cầm client secret.
     *
     * @param code Authorization code Google trả về sau khi user đăng nhập
     * @param redirectUri PHẢI khớp y hệt redirect_uri đã dùng lúc tạo authorization URL
     * @return Google ID token (JWT) — đưa tiếp vào {@link #verifyToken(String)} để tái dùng logic hiện có
     */
    public String exchangeCodeForIdToken(String code, String redirectUri) throws IOException {
        NetHttpTransport transport = new NetHttpTransport();
        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                transport, new GsonFactory(), clientId, clientSecret, code, redirectUri)
                .execute();

        String idToken = tokenResponse.getIdToken();
        if (idToken == null) {
            throw new RuntimeException("Google không trả về id_token khi đổi authorization code");
        }
        return idToken;
    }
}
