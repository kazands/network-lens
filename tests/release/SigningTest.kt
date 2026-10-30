import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/** Build-tool integration checks use isolated generated keys, never the real release credentials. */
object SigningTest {
    private var assertions = 0
    private val project = Paths.get("").toAbsolutePath()
    private val privateFile = PosixFilePermissions.fromString("rw-------")
    private val privateDirectory = PosixFilePermissions.fromString("rwx------")
    private lateinit var fixture: Path
    private val roleFiles = listOf("network-lens-app-signing.p12", "network-lens-app-signing-password",
        "network-lens-upload.p12", "network-lens-upload-password")
    private val secrets = mutableListOf<String>()

    private fun check(ok: Boolean, message: String) {
        assertions++
        if (!ok) throw AssertionError(message)
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun bytes(path: Path) = Files.readAllBytes(path)
    private fun text(path: Path) = String(bytes(path), Charsets.UTF_8)
    private fun write(path: Path, data: ByteArray) { Files.write(path, data); Files.setPosixFilePermissions(path, privateFile) }
    private fun command(vararg args: String): Pair<Int, String> {
        val log = Files.createTempFile(fixture, "command-", ".txt")
        Files.setPosixFilePermissions(log, privateFile)
        val process = ProcessBuilder(*args).directory(fixture.toFile()).redirectErrorStream(true).redirectOutput(log.toFile())
        process.environment()["ANDROID_SDK_ROOT"] = System.getenv("ANDROID_SDK_ROOT") ?: project.resolve(".tools/sdk").toString()
        val run = process.start()
        val finished = run.waitFor(120, TimeUnit.SECONDS)
        if (!finished) { run.destroyForcibly(); run.waitFor(5, TimeUnit.SECONDS) }
        check(finished, "Signing fixture command finishes within its deadline")
        val output = text(log)
        check(secrets.none { output.contains(it) }, "No fixture password appears in command output")
        Files.delete(log)
        return Pair(run.exitValue(), output)
    }
    private fun cli(vararg args: String) = command("python3", fixture.resolve("scripts/release-signing.py").toString(), *args)
    private fun expectedFailure(vararg args: String) { check(cli(*args).first != 0, "Invalid signing state is rejected") }
    private fun storeFingerprint(path: Path, passwordPath: Path, alias: String, bits: Int = 4096): String {
        val password = text(passwordPath).trim().toCharArray()
        try {
            val store = KeyStore.getInstance("PKCS12")
            Files.newInputStream(path).use { store.load(it, password) }
            check(store.isKeyEntry(alias), "Role alias contains a private key")
            val cert = store.getCertificate(alias)
            check((cert.publicKey as RSAPublicKey).modulus.bitLength() == bits, "Fixture key size matches its role")
            return sha(cert.encoded)
        } finally { password.fill('\u0000') }
    }
    private fun payload(path: Path): Map<String, String> = ZipFile(path.toFile()).use { zip ->
        val oldSignature = setOf("META-INF/MANIFEST.MF", "META-INF/NETLENS.SF", "META-INF/NETLENS.RSA")
        zip.entries().toList().filter { !it.isDirectory && it.name !in oldSignature }.associate { entry ->
            entry.name to zip.getInputStream(entry).use { sha(it.readBytes()) }
        }
    }
    private fun tool(name: String): String {
        val sdk = Paths.get(System.getenv("ANDROID_SDK_ROOT") ?: project.resolve(".tools/sdk").toString())
        val directories = System.getenv("NETLENS_BUILD_TOOLS")?.let { listOf(Paths.get(it)) }
            ?: listOf(sdk.resolve("android-16"), sdk.resolve("build-tools/36.0.0"))
        return directories.map { it.resolve(name) }.first { Files.isRegularFile(it) }.toString()
    }

    @JvmStatic fun main(args: Array<String>) {
        fixture = Files.createTempDirectory(project.resolve(".tools"), "release-signing-fixture-")
        Files.setPosixFilePermissions(fixture, privateDirectory)
        Files.createDirectories(fixture.resolve("scripts"))
        Files.createDirectories(fixture.resolve("android"))
        for (name in listOf("release-signing.py", "build.sh", "prepare-signing.py")) {
            Files.copy(project.resolve("scripts/$name"), fixture.resolve("scripts/$name"))
        }
        val keyDir = fixture.resolve(".tools/release-signing")
        val identity = fixture.resolve("android/release-signing.json")
        val receipt = keyDir.resolve("backup-verification.json")
        val originalApk = project.resolve("artifacts/Network-Lens-0.3.9.apk")
        check(Files.isRegularFile(originalApk), "Build the development APK before these integration checks")
        val originalHash = sha(bytes(originalApk))
        expectedFailure("check")
        check(!Files.exists(keyDir), "A missing identity never creates keys")
        check(command("bash", fixture.resolve("scripts/build.sh").toString(), "--release").first != 0,
            "Release preflight fails before compilation when identity is missing")
        check(!Files.exists(fixture.resolve(".tools/build-release")), "Rejected release leaves build output untouched")
        check(!Files.exists(fixture.resolve(".tools/netlens-development.jks")), "Rejected release does not automatically create a test key")
        val keytool = Paths.get(System.getProperty("java.home")).resolve("bin/keytool").toString()
        check(command("python3", fixture.resolve("scripts/prepare-signing.py").toString(), "--keytool", keytool).first == 0,
            "Create a separate synthetic legacy test identity")
        val testKey = fixture.resolve(".tools/netlens-development.jks")
        val testPassword = fixture.resolve(".tools/netlens-signing-password")
        val testKeyBytes = bytes(testKey); val testPasswordBytes = bytes(testPassword)
        secrets.add(text(testPassword).trim())
        val testFingerprint = storeFingerprint(testKey, testPassword, "netlens", 2048)
        val init = cli("init")
        check(init.first == 0, "Explicit one-time initialization succeeds")
        check(Files.getPosixFilePermissions(keyDir) == privateDirectory, "Private signing directory is owner-only")
        for (name in roleFiles) check(Files.getPosixFilePermissions(keyDir.resolve(name)) == privateFile, "Each signing file is owner-only")
        for (name in listOf(roleFiles[1], roleFiles[3])) secrets.add(text(keyDir.resolve(name)).trim())
        check(secrets.distinct().size == 3 && secrets.all { it.length >= 64 }, "Test, app and upload roles have distinct strong generated passwords")
        val originalFiles = roleFiles.associateWith { bytes(keyDir.resolve(it)) }
        val metadata = bytes(identity)
        val appFingerprint = storeFingerprint(keyDir.resolve(roleFiles[0]), keyDir.resolve(roleFiles[1]), "network-lens-app")
        val uploadFingerprint = storeFingerprint(keyDir.resolve(roleFiles[2]), keyDir.resolve(roleFiles[3]), "network-lens-upload")
        check(setOf(testFingerprint, appFingerprint, uploadFingerprint).size == 3 && text(identity).contains(appFingerprint) && text(identity).contains(uploadFingerprint) && text(identity).contains(testFingerprint),
            "Distinct app and upload certificates are pinned to actual generated keys")
        expectedFailure("init")
        check(roleFiles.all { bytes(keyDir.resolve(it)).contentEquals(originalFiles.getValue(it)) } && bytes(identity).contentEquals(metadata),
            "Repeated init preserves both keys, passwords and pinned identity")
        check(cli("inspect").first == 0, "Both keys can be inspected before backup verification")
        expectedFailure("check")
        expectedFailure("verify-backup", "--restored-directory", keyDir.toString())
        check(!Files.exists(receipt), "Original files cannot create a recovery receipt")

        val destination = fixture.resolve("artifacts/release/fixture.apk")
        expectedFailure("sign-apk", "--input", originalApk.toString(), "--output", destination.toString())
        check(!Files.exists(destination), "A key without a verified backup cannot sign a release")
        val restored = fixture.resolve("downloaded-recovery-copies")
        Files.createDirectory(restored); Files.setPosixFilePermissions(restored, privateDirectory)
        for (name in roleFiles) write(restored.resolve(name), originalFiles.getValue(name))
        Files.delete(restored.resolve(roleFiles[0]))
        Files.createLink(restored.resolve(roleFiles[0]), keyDir.resolve(roleFiles[0]))
        expectedFailure("verify-backup", "--restored-directory", restored.toString())
        check(!Files.exists(receipt), "A hard-linked original is not an independent recovery copy")
        Files.delete(restored.resolve(roleFiles[0])); write(restored.resolve(roleFiles[0]), originalFiles.getValue(roleFiles[0]))
        write(restored.resolve(roleFiles[3]), "synthetic-wrong-password\n".toByteArray())
        expectedFailure("verify-backup", "--restored-directory", restored.toString())
        check(!Files.exists(receipt), "Mismatched backup data never creates a successful receipt")
        write(restored.resolve(roleFiles[3]), originalFiles.getValue(roleFiles[3]))
        check(cli("verify-backup", "--restored-directory", restored.toString()).first == 0, "Separate exact copies pass recovery verification")
        check(cli("check").first == 0, "Verified app and upload roles become ready")
        val goodReceipt = bytes(receipt)
        val uploadPassword = text(keyDir.resolve(roleFiles[3])).trim().toCharArray()
        val entryPassword = "synthetic-distinct-entry-password".toCharArray()
        try {
            val store = KeyStore.getInstance("PKCS12")
            Files.newInputStream(keyDir.resolve(roleFiles[2])).use { store.load(it, uploadPassword) }
            val key = store.getKey("network-lens-upload", uploadPassword)
            val chain = store.getCertificateChain("network-lens-upload")
            store.setKeyEntry("network-lens-upload", key, entryPassword, chain)
            Files.newOutputStream(keyDir.resolve(roleFiles[2])).use { store.store(it, uploadPassword) }
            write(restored.resolve(roleFiles[2]), bytes(keyDir.resolve(roleFiles[2])))
            expectedFailure("inspect", "--role", "upload")
            Files.delete(receipt)
            expectedFailure("verify-backup", "--restored-directory", restored.toString())
            check(!Files.exists(receipt), "A readable certificate with an unusable private entry never receives a recovery receipt")
        } finally {
            uploadPassword.fill('\u0000'); entryPassword.fill('\u0000')
            write(keyDir.resolve(roleFiles[2]), originalFiles.getValue(roleFiles[2]))
            write(restored.resolve(roleFiles[2]), originalFiles.getValue(roleFiles[2]))
            write(receipt, goodReceipt)
        }
        val request = command(keytool, "-certreq", "-keystore", keyDir.resolve(roleFiles[0]).toString(),
            "-storepass:file", keyDir.resolve(roleFiles[1]).toString(), "-keypass:file", keyDir.resolve(roleFiles[1]).toString(),
            "-alias", "network-lens-app")
        check(request.first == 0, "Synthetic private key signs a certificate request")
        val encoded = request.second.lineSequence().filter { it.isNotBlank() && !it.startsWith("-----") }.joinToString("")
        val corrupted = java.util.Base64.getDecoder().decode(encoded)
        corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 1).toByte()
        val corruptedRequest = fixture.resolve("tampered-request.pem")
        write(corruptedRequest, ("-----BEGIN NEW CERTIFICATE REQUEST-----\n" +
            java.util.Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(corrupted) +
            "\n-----END NEW CERTIFICATE REQUEST-----\n").toByteArray())
        check(command(keytool, "-printcertreq", "-file", corruptedRequest.toString()).first != 0,
            "The JDK request reader rejects a changed signature, not just malformed certificate syntax")

        Files.setPosixFilePermissions(keyDir.resolve(roleFiles[1]), PosixFilePermissions.fromString("rw-r--r--"))
        expectedFailure("check")
        Files.setPosixFilePermissions(keyDir.resolve(roleFiles[1]), privateFile)
        write(identity, String(metadata, Charsets.UTF_8).replace(appFingerprint, "0".repeat(64)).toByteArray())
        expectedFailure("inspect")
        write(identity, metadata)
        val duplicate = String(metadata, Charsets.UTF_8).replace("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1")
        write(identity, duplicate.toByteArray()); expectedFailure("inspect"); write(identity, metadata)
        val receiptBytes = bytes(receipt)
        write(receipt, text(receipt).replace(Regex("(\"keystoreSha256\": )\"[0-9a-f]{64}\""), "$1\"${"0".repeat(64)}\"").toByteArray())
        expectedFailure("check"); write(receipt, receiptBytes)
        write(keyDir.resolve(roleFiles[1]), "synthetic-invalid-password\n".toByteArray())
        expectedFailure("check"); write(keyDir.resolve(roleFiles[1]), originalFiles.getValue(roleFiles[1]))
        val savedKey = keyDir.resolve("app-key.saved")
        Files.move(keyDir.resolve(roleFiles[0]), savedKey)
        Files.createSymbolicLink(keyDir.resolve(roleFiles[0]), savedKey.fileName)
        expectedFailure("inspect")
        Files.delete(keyDir.resolve(roleFiles[0]))
        check(command("bash", fixture.resolve("scripts/build.sh").toString(), "--release").first != 0,
            "A missing private key fails release preflight")
        check(bytes(testKey).contentEquals(testKeyBytes) && bytes(testPassword).contentEquals(testPasswordBytes), "Release failure never changes existing test credentials")
        Files.move(savedKey, keyDir.resolve(roleFiles[0]))
        expectedFailure("sign-apk", "--input", originalApk.toString(), "--output", fixture.resolve("artifacts/wrong-directory.apk").toString())
        check(cli("sign-apk", "--input", originalApk.toString(), "--output", destination.toString()).first == 0, "A verified app key signs the expected package")
        val signature = command(tool("apksigner"), "verify", "--verbose", "--print-certs", destination.toString())
        check(signature.first == 0 && signature.second.contains("Signer #1 certificate SHA-256 digest: $appFingerprint") && !signature.second.contains(uploadFingerprint),
            "The release verifies with the app certificate, never the upload certificate")
        check(payload(originalApk) == payload(destination), "Signing changes no packaged application payload")
        check(sha(bytes(originalApk)) == originalHash, "The accepted development APK is preserved")
        val signedHash = sha(bytes(destination))
        val manifest = fixture.resolve("wrong-package.xml")
        write(manifest, "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"dev.synthetic.signing\"><uses-sdk android:minSdkVersion=\"26\"/><application/></manifest>".toByteArray())
        val sdk = Paths.get(System.getenv("ANDROID_SDK_ROOT") ?: project.resolve(".tools/sdk").toString())
        val platform = listOf(sdk.resolve("android-37.0/android.jar"), sdk.resolve("platforms/android-37.0/android.jar")).first { Files.isRegularFile(it) }
        val wrongPackage = fixture.resolve("wrong-package.apk")
        check(command(tool("aapt2"), "link", "-o", wrongPackage.toString(), "--manifest", manifest.toString(), "-I", platform.toString()).first == 0,
            "Build a synthetic different-package APK fixture")
        expectedFailure("sign-apk", "--input", wrongPackage.toString(), "--output", destination.toString())
        check(sha(bytes(destination)) == signedHash, "Rejected input cannot replace an existing valid release")
        check(roleFiles.all { bytes(keyDir.resolve(it)).contentEquals(originalFiles.getValue(it)) }, "All signing fixtures retain their original key and password bytes")
        check(bytes(testKey).contentEquals(testKeyBytes) && bytes(testPassword).contentEquals(testPasswordBytes), "All initialization, backup and signing paths preserve the test identity")
        // Fixture-only credentials are destroyed after every successful run. No real signing key is read.
        fixture.toFile().deleteRecursively()
        println("PASS · $assertions release-signing assertions using isolated generated credentials")
    }
}
