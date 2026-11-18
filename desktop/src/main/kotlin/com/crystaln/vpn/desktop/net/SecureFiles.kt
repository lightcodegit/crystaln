package com.crystaln.vpn.desktop.net

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Writes files that may contain credentials with restrictive permissions.
 * On POSIX systems the file is created as 0600; on other systems we fall back
 * to setting the writable flag and rely on the OS ACLs.
 */
internal object SecureFiles {
    fun writeText(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        try {
            Files.setPosixFilePermissions(
                tmp.toPath(),
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )
        } catch (_: Throwable) {
            // Windows / non-POSIX FS: best effort.
            tmp.setReadable(false, false)
            tmp.setWritable(true, true)
        }
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            try {
                Files.setPosixFilePermissions(
                    file.toPath(),
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                )
            } catch (_: Throwable) {
            }
            tmp.delete()
        }
    }
}
