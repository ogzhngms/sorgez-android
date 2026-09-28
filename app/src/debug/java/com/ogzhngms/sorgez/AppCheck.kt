package com.ogzhngms.sorgez

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// Debug builds prove themselves with a debug token: it is printed to logcat on the first run
// and must be added under App Check > Apps > Manage debug tokens in the Firebase console.
internal fun appCheckFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
