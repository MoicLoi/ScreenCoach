package com.moiclathy.screencoach.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small Android Keystore wrapper for the DeepSeek API key.
 * Values never leave the private app sandbox in plaintext preferences.
 */
public final class SecretStore {
    private static final String TAG = "SecretStore";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "screencoach.secret.v1";
    private static final String PREFIX = "v1:";
    private final SharedPreferences prefs;

    public SecretStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences("screencoach_secrets", Context.MODE_PRIVATE);
    }

    public String get(String name) {
        String encoded = prefs.getString(name, "");
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            String[] parts = encoded.split(":", 3);
            if (parts.length != 3 || !"v1".equals(parts[0])) return "";
            byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to decrypt secret " + name, t);
            return "";
        }
    }

    public boolean put(String name, String value) {
        if (value == null || value.isEmpty()) {
            prefs.edit().remove(name).apply();
            return true;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // Let Keystore generate the randomized IV. Supplying our own IV is
            // rejected when the key requires randomized encryption.
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            if (iv == null || iv.length == 0) {
                throw new IllegalStateException("Keystore returned no IV");
            }
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String encoded = PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP)
                    + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            return prefs.edit().putString(name, encoded).commit();
        } catch (Throwable t) {
            Log.e(TAG, "Unable to encrypt secret " + name, t);
            return false;
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }
}
