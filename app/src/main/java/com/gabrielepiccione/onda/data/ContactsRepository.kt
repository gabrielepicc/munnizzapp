package com.gabrielepiccione.onda.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = 5): List<ContactHit> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !hasPermission()) return@withContext emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        val result = mutableListOf<ContactHit>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && result.size < limit) {
                    val name = c.getString(2) ?: continue
                    result += ContactHit(
                        id = c.getLong(0),
                        lookupKey = c.getString(1) ?: "",
                        name = name,
                        photoUri = c.getString(3),
                    )
                }
            }
        }
        result
    }

    fun open(hit: ContactHit) {
        val uri = ContactsContract.Contacts.getLookupUri(hit.id, hit.lookupKey)
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
