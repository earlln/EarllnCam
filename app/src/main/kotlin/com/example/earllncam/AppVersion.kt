package com.example.earllncam

import android.content.Context

/**
 * The on-screen version badge ("EarllnCam v1.1.0 by Earlln.com").
 *
 * The version itself is declared once in version.properties at the repo root;
 * the build turns it into versionName/versionCode and BuildConfig hands it
 * back here, so bumping that one file is all a release needs.
 */
object AppVersion {

    const val NAME: String = BuildConfig.VERSION_NAME
    const val CODE: Int = BuildConfig.VERSION_CODE

    fun label(context: Context): String = context.getString(R.string.version_label, NAME)
}
