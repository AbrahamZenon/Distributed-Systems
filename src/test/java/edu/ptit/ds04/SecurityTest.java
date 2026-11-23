package edu.ptit.ds04;

import edu.ptit.ds04.common.Config;
import edu.ptit.ds04.common.SecurityUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security and Invalid-Data Tests fulfilling Course Specification Section 9.
 * Author: Nguyen Duy Phuc (B23DCVT341)
 */
public class SecurityTest {

    @Test
    public void testTokenAuthenticationAndTamperResistance() {
        String secret = Config.AUTH_TOKEN_SECRET;
        String token = SecurityUtil.generateToken("alice", "editor", secret);
        assertNotNull(token);

        // Valid token
        SecurityUtil.AuthResult auth = SecurityUtil.verifyToken(token, secret);
        assertTrue(auth.valid, "Valid HMAC token should pass authentication");
        assertEquals("alice", auth.clientId);
        assertEquals("editor", auth.role);

        // Tampered signature
        String tampered = token.substring(0, token.length() - 2) + "ff";
        SecurityUtil.AuthResult badAuth = SecurityUtil.verifyToken(tampered, secret);
        assertFalse(badAuth.valid, "Tampered HMAC token must be rejected");

        // Null / empty token
        assertFalse(SecurityUtil.verifyToken("", secret).valid);
        assertFalse(SecurityUtil.verifyToken(null, secret).valid);
    }

    @Test
    public void testOperationPayloadSanitizationAndValidation() {
        // Valid insert
        JSONObject validInsert = new JSONObject();
        validInsert.put("op_type", "INSERT");
        validInsert.put("position", 0);
        validInsert.put("text", "hello");
        assertTrue(SecurityUtil.sanitizeOperationPayload(validInsert));

        // Invalid negative position
        JSONObject negativePos = new JSONObject();
        negativePos.put("op_type", "INSERT");
        negativePos.put("position", -5);
        negativePos.put("text", "hack");
        assertFalse(SecurityUtil.sanitizeOperationPayload(negativePos), "Negative positions must be rejected");

        // Invalid unknown operation type
        JSONObject badType = new JSONObject();
        badType.put("op_type", "DROP_DATABASE");
        badType.put("position", 0);
        badType.put("text", "evil");
        assertFalse(SecurityUtil.sanitizeOperationPayload(badType), "Unknown op_type must be rejected");

        // Payload size limit check (> 1MB)
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 1050000; i++) {
            large.append("a");
        }
        assertFalse(SecurityUtil.validatePayloadSize(large.toString()), "Payload exceeding MAX_PAYLOAD_BYTES must be rejected");
        assertTrue(SecurityUtil.validatePayloadSize("Small valid payload"));
    }
}
