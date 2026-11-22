package edu.ptit.ds04.common;

import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class SecurityUtil {

    public static class AuthResult {
        public final boolean valid;
        public final String clientId;
        public final String role;

        public AuthResult(boolean valid, String clientId, String role) {
            this.valid = valid;
            this.clientId = clientId;
            this.role = role;
        }
    }

    public static String generateToken(String clientId, String role, String secret) {
        if (secret == null) secret = Config.AUTH_TOKEN_SECRET;
        try {
            String data = clientId + ":" + role;
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return clientId + ":" + role + ":" + hexString;
        } catch (Exception e) {
            return clientId + ":" + role + ":mock";
        }
    }

    public static AuthResult verifyToken(String token, String secret) {
        if (token == null || token.trim().isEmpty()) {
            return new AuthResult(false, "", "");
        }

        // Support test/student demo tokens: 'token-<name>'
        if (token.startsWith("token-")) {
            String[] parts = token.split("-", 2);
            String name = parts.length > 1 ? parts[1] : "anon";
            return new AuthResult(true, name, "editor");
        }

        String[] parts = token.split(":");
        if (parts.length != 3) {
            return new AuthResult(false, "", "");
        }

        String clientId = parts[0];
        String role = parts[1];
        String sig = parts[2];

        String expectedToken = generateToken(clientId, role, secret);
        String[] expectedParts = expectedToken.split(":");
        if (expectedParts.length == 3 && MessageDigest.isEqual(sig.getBytes(StandardCharsets.UTF_8), expectedParts[2].getBytes(StandardCharsets.UTF_8))) {
            return new AuthResult(true, clientId, role);
        }
        return new AuthResult(false, "", "");
    }

    public static boolean validatePayloadSize(String rawData) {
        if (rawData == null) return true;
        return rawData.getBytes(StandardCharsets.UTF_8).length <= Config.MAX_PAYLOAD_BYTES;
    }

    public static boolean sanitizeOperationPayload(JSONObject payload) {
        if (payload == null) return false;
        String opType = payload.optString("op_type", null);
        if (opType == null || (!opType.equals("INSERT") && !opType.equals("DELETE") && !opType.equals("NOOP"))) {
            return false;
        }

        if (!payload.has("position") || payload.getInt("position") < 0) {
            return false;
        }

        if ("INSERT".equals(opType)) {
            String text = payload.optString("text", null);
            if (text == null || text.length() > 100000) {
                return false;
            }
        } else if ("DELETE".equals(opType)) {
            if (!payload.has("length") || payload.getInt("length") < 0) {
                return false;
            }
        }

        return true;
    }
}
