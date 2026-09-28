package com.ogzhngms.sorgez

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

// Release builds prove themselves with Play Integrity, which works once the app is installed from Google Play.
internal fun appCheckFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
