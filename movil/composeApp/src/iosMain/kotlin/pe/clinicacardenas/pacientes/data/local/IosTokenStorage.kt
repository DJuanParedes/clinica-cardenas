@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package pe.clinicacardenas.pacientes.data.local

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import pe.clinicacardenas.pacientes.domain.repository.TokenStorage

/** Almacén seguro del token en iOS: el Keychain del sistema, accesible solo en este dispositivo. */
class IosTokenStorage : TokenStorage {
    private val service = "pe.clinicacardenas.pacientes.sesion"

    private fun query(key: String, extra: (CFMutableDictionaryRef?) -> Unit = {}): CFMutableDictionaryRef? {
        val d = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        CFDictionaryAddValue(d, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(d, kSecAttrService, CFBridgingRetain(service))
        CFDictionaryAddValue(d, kSecAttrAccount, CFBridgingRetain(key))
        extra(d)
        return d
    }

    override fun read(key: String): String? = memScoped {
        val q = query(key) {
            CFDictionaryAddValue(it, kSecReturnData, kCFBooleanTrue)
            CFDictionaryAddValue(it, kSecMatchLimit, kSecMatchLimitOne)
        }
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(q, result.ptr)
        CFRelease(q)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun write(key: String, value: String?) {
        val del = query(key)
        SecItemDelete(del)
        CFRelease(del)
        if (value == null) return
        @Suppress("CAST_NEVER_SUCCEEDS")
        val data: NSData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val add = query(key) {
            CFDictionaryAddValue(it, kSecValueData, CFBridgingRetain(data))
            CFDictionaryAddValue(it, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        }
        SecItemAdd(add, null)
        CFRelease(add)
    }
}
