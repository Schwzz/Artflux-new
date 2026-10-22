package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SourceCredentialVault
import com.example.data.SourceCredentials
import com.example.data.SourceRepository
import com.example.model.MediaSourceConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CredentialFailureTest {
    @Test fun unavailableKeyDoesNotStoreCredentialsOrOverwriteExistingSource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("media_browser_sources", Context.MODE_PRIVATE).edit().clear().commit()
        val vault = SourceCredentialVault(context) { throw GeneralSecurityException("Keystore unavailable") }
        val repo = SourceRepository(context, vault)
        val original = MediaSourceConfig(id = "custom", name = "Original", apiUrl = "https://example.org")
        assertTrue(repo.addSource(original))
        assertFalse(repo.updateSource(original.copy(name = "Edited", apiKey = "secret")))
        assertEquals(original, repo.getSourceById("custom"))
        assertFalse(vault.saveCredentials("new", SourceCredentials(apiKey = "secret")))
        assertNull(context.getSharedPreferences("artflux_secure_credentials", Context.MODE_PRIVATE).getString("cred_new", null))
    }

    @Test fun failedMigrationRetainsOriginalDataForRetry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("media_browser_sources", Context.MODE_PRIVATE)
        val legacy = MediaSourceConfig(id = "legacy", name = "Legacy", apiUrl = "https://example.org", apiKey = "legacy-secret")
        val json = org.json.JSONArray().put(legacy.toJson()).toString()
        prefs.edit().putString("custom_sources", json).commit()
        val vault = SourceCredentialVault(context) { throw GeneralSecurityException("Unavailable") }
        val repo = SourceRepository(context, vault)
        assertEquals(json, prefs.getString("custom_sources", null))
        assertFalse(repo.addSource(MediaSourceConfig(id = "new", name = "New", apiUrl = "https://example.org/new")))
        assertEquals(json, prefs.getString("custom_sources", null))
    }

    @Test fun injectedTestKeySupportsPersistenceAcrossVaultInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertTrue(SourceCredentialVault(context) { key }.saveCredentials("roundtrip", SourceCredentials(apiKey = "secret")))
        assertEquals("secret", SourceCredentialVault(context) { key }.getCredentials("roundtrip").apiKey)
    }
}
