package com.umesh.unipay_1.service;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import com.umesh.unipay_1.exception.BusinessException;
import com.umesh.unipay_1.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthService {

    @Value("${app.firebase.api-key}")
    private String firebaseApiKey;

    private final RestTemplate restTemplate;

    /**
     * Verify a Firebase ID token (used during Firebase-token-based login).
     */
    public FirebaseToken verifyIdToken(String idToken) {
        try {
            return FirebaseAuth.getInstance().verifyIdToken(idToken);
        } catch (FirebaseAuthException e) {
            log.error("Firebase Token Verification failed: {}", e.getMessage());
            throw new UnauthorizedException("Invalid Firebase token");
        }
    }

    /**
     * Create a new user in Firebase (used during registration).
     */
    public UserRecord createFirebaseUser(String email, String password, String name) {
        try {
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(name);
            return FirebaseAuth.getInstance().createUser(request);
        } catch (FirebaseAuthException e) {
            log.error("Firebase User Creation Failed [{}]: {}", e.getErrorCode(), e.getMessage());
            // "auth/email-already-exists" is the standard Firebase Admin SDK error code
            if ("auth/email-already-exists".equals(e.getErrorCode())) {
                throw new BusinessException("Email already registered in Firebase");
            }
            throw new BusinessException("Failed to create user account. Please try again.");
        }
    }

    /**
     * Delete a Firebase user — used for rollback when DB save fails after Firebase creation.
     */
    public void deleteFirebaseUser(String firebaseUid) {
        try {
            FirebaseAuth.getInstance().deleteUser(firebaseUid);
        } catch (FirebaseAuthException e) {
            log.error("Failed to delete Firebase user {}: {}", firebaseUid, e.getMessage());
        }
    }

    /**
     * Authenticate via Firebase REST API using email and password.
     * Returns the Firebase UID (localId) on success.
     */
    public String signInWithEmailPassword(String email, String password) {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + firebaseApiKey;

        Map<String, String> body = Map.of(
                "email", email,
                "password", password,
                "returnSecureToken", "true"
        );
        try {
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            if (response == null || !response.containsKey("localId")) {
                throw new UnauthorizedException("Invalid login response from Firebase");
            }
            return (String) response.get("localId"); // Firebase UID
        } catch (HttpClientErrorException e) {
            log.warn("Firebase signIn failed [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new UnauthorizedException("Invalid email or password");
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Firebase sign-in: {}", e.getMessage());
            throw new UnauthorizedException("Login failed. Please try again.");
        }
    }
}
