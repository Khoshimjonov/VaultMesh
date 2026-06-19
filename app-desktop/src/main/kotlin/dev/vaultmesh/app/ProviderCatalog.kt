package dev.vaultmesh.app

/** A credential field a non-OAuth provider needs the user to fill in. */
data class ProviderField(val key: String, val label: String, val password: Boolean = false)

/**
 * A storage provider the user can connect in-app. OAuth providers authorize via the browser
 * (rclone handles the flow); credential providers collect [fields] (keys/passwords) in a form.
 * [fixedParams] are non-secret defaults passed straight to rclone.
 */
data class Provider(
    val type: String,
    val displayName: String,
    val oauth: Boolean,
    val defaultRemoteName: String,
    val fields: List<ProviderField> = emptyList(),
    val fixedParams: Map<String, String> = emptyMap(),
)

object ProviderCatalog {
    val all: List<Provider> = listOf(
        Provider("drive", "Google Drive", oauth = true, defaultRemoteName = "gdrive", fixedParams = mapOf("scope" to "drive")),
        Provider("onedrive", "OneDrive", oauth = true, defaultRemoteName = "onedrive"),
        Provider("dropbox", "Dropbox", oauth = true, defaultRemoteName = "dropbox"),
        Provider("box", "Box", oauth = true, defaultRemoteName = "box"),
        Provider("pcloud", "pCloud", oauth = true, defaultRemoteName = "pcloud"),
        Provider("yandex", "Yandex Disk", oauth = true, defaultRemoteName = "yandex"),
        Provider(
            "mega", "Mega", oauth = false, defaultRemoteName = "mega",
            fields = listOf(ProviderField("user", "Email"), ProviderField("pass", "Password", password = true)),
        ),
        Provider(
            "s3", "Amazon S3", oauth = false, defaultRemoteName = "s3",
            fixedParams = mapOf("provider" to "AWS"),
            fields = listOf(
                ProviderField("access_key_id", "Access key ID"),
                ProviderField("secret_access_key", "Secret access key", password = true),
                ProviderField("region", "Region (e.g. us-east-1)"),
            ),
        ),
        Provider(
            "b2", "Backblaze B2", oauth = false, defaultRemoteName = "b2",
            fields = listOf(
                ProviderField("account", "Account / Key ID"),
                ProviderField("key", "Application key", password = true),
            ),
        ),
    )
}
