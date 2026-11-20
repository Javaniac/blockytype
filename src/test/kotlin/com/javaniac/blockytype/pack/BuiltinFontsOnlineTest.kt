package com.javaniac.blockytype.pack

import org.junit.jupiter.api.Tag
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertTrue

/** Downloads every font of the manifest and runs the real font check on it. Not part of `test`: run `./gradlew verifyBuiltinFonts`. */
@Tag("online")
class BuiltinFontsOnlineTest {
    @Test
    fun `every manifest font downloads, matches its hash and passes the monospace and weight check`() {
        val dir = Files.createTempDirectory("builtin-online")
        val result = FontInstaller.install(FontManifest.ALL, dir)
        assertTrue(result.failed.isEmpty(), "download failures: ${result.failed}")
        for (f in FontManifest.ALL) {
            val info = FontInfo.load(f.name, dir.resolve("${f.name}.ttf").readBytes(), "ttf")
            assertTrue(info.advanceRatio > 0.3 && info.advanceRatio < 1.0, "${f.name} ratio ${info.advanceRatio}")
            assertTrue(info.monoSize in 4.0..40.0, "${f.name} size ${info.monoSize}")
            assertTrue(dir.resolve("${f.name}.license.txt").readBytes().size > 200, "${f.name} license")
        }
    }
}
