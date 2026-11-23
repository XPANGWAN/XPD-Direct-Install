package com.xpd.directinstall

object TransferRuntime {
    @Volatile
    var server: PkgHttpServer? = null
}
