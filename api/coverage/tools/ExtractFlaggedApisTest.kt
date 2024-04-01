package android.platform.coverage

import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.google.protobuf.TextFormat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ExtractFlaggedApisTest {

    companion object {
        const val COMMAND = "java -jar extract-flagged-apis.jar %s %s"
    }

    var apiTextFile: Path = Files.createTempFile("current", ".txt")
    var flagToApiMap: Path = Files.createTempFile("flag_api_map", ".textproto")

    @Before
    fun setup() {
        apiTextFile = Files.createTempFile("current", ".txt")
        flagToApiMap = Files.createTempFile("flag_api_map", ".textproto")
    }

    @After
    fun cleanup() {
        Files.deleteIfExists(apiTextFile)
        Files.deleteIfExists(flagToApiMap)
    }

    @Test
    fun extractFlaggedApis_onlyMethodFlag_useMethodFlag() {
        val apiText =
            """
            // Signature format: 2.0
            package android.net.ipsec.ike {
              public final class IkeSession implements java.lang.AutoCloseable {
                method @FlaggedApi("com.android.ipsec.flags.dumpsys_api") public void dump(@NonNull java.io.PrintWriter);
              }
            }
        """
                .trimIndent()
        Files.write(apiTextFile, apiText.toByteArray(Charsets.UTF_8), StandardOpenOption.APPEND)

        val command =
            String.format(COMMAND, apiTextFile.toAbsolutePath(), flagToApiMap.toAbsolutePath())
        val process = Runtime.getRuntime().exec(command)
        process.waitFor()

        val content = Files.readAllBytes(flagToApiMap).toString(Charsets.UTF_8)
        val result = TextFormat.parse(content, FlagApiMap::class.java)

        val expected = FlagApiMap.newBuilder()
        val api =
            JavaMethod.newBuilder()
                .setPackageName("android.net.ipsec.ike")
                .setClassName("IkeSession")
                .setMethodName("dump")
        api.addParameters("java.io.PrintWriter")
        addFlaggedApi(expected, api, "com.android.ipsec.flags.dumpsys_api")
        assertThat(result).isEqualTo(expected.build())
    }

    @Test
    fun extractFlaggedApis_onlyClassFlag_useClassFlag() {
        val apiText =
            """
            // Signature format: 2.0
            package android.net.ipsec.ike {
              @FlaggedApi("com.android.ipsec.flags.dumpsys_api") public final class IkeSession implements java.lang.AutoCloseable {
                method public void dump(@NonNull java.io.PrintWriter);
              }
            }
        """
                .trimIndent()
        Files.write(apiTextFile, apiText.toByteArray(Charsets.UTF_8), StandardOpenOption.APPEND)

        val command =
            String.format(COMMAND, apiTextFile.toAbsolutePath(), flagToApiMap.toAbsolutePath())
        val process = Runtime.getRuntime().exec(command)
        process.waitFor()

        val content = Files.readAllBytes(flagToApiMap).toString(Charsets.UTF_8)
        val result = TextFormat.parse(content, FlagApiMap::class.java)

        val expected = FlagApiMap.newBuilder()
        val api =
            JavaMethod.newBuilder()
                .setPackageName("android.net.ipsec.ike")
                .setClassName("IkeSession")
                .setMethodName("dump")
        api.addParameters("java.io.PrintWriter")
        addFlaggedApi(expected, api, "com.android.ipsec.flags.dumpsys_api")
        assertThat(result).isEqualTo(expected.build())
    }

    @Test
    fun extractFlaggedApis_flaggedConstructorsAreFlaggedApis() {
        val apiText =
            """
            // Signature format: 2.0
            package android.app.pinner {
              @FlaggedApi("android.app.pinner_service_client_api") public class PinnerServiceClient {
                ctor @FlaggedApi("android.app.pinner_service_client_api") public PinnerServiceClient();
              }
            }
        """
                .trimIndent()
        Files.write(apiTextFile, apiText.toByteArray(Charsets.UTF_8), StandardOpenOption.APPEND)

        val command =
            String.format(COMMAND, apiTextFile.toAbsolutePath(), flagToApiMap.toAbsolutePath())
        val process = Runtime.getRuntime().exec(command)
        process.waitFor()

        val content = Files.readAllBytes(flagToApiMap).toString(Charsets.UTF_8)
        val result = TextFormat.parse(content, FlagApiMap::class.java)

        val expected = FlagApiMap.newBuilder()
        val api =
            JavaMethod.newBuilder()
                .setPackageName("android.app.pinner")
                .setClassName("PinnerServiceClient")
                .setMethodName("PinnerServiceClient")
        addFlaggedApi(expected, api, "android.app.pinner_service_client_api")
        assertThat(result).isEqualTo(expected.build())
    }

    private fun addFlaggedApi(builder: FlagApiMap.Builder, api: JavaMethod.Builder, flag: String) {
        if (builder.containsFlagToApi(flag)) {
            val updatedApis =
                builder.getFlagToApiOrThrow(flag).toBuilder().addJavaMethods(api).build()
            builder.putFlagToApi(flag, updatedApis)
        } else {
            val apis = FlaggedApis.newBuilder().addJavaMethods(api).build()
            builder.putFlagToApi(flag, apis)
        }
    }
}
