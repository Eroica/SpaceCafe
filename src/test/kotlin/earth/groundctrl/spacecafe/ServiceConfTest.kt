package earth.groundctrl.spacecafe

import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import kotlin.test.Test

internal class ServiceConfTest {
    @Test
    fun `test load`() {
        assertDoesNotThrow {
            ServiceConf.load(File(javaClass.classLoader.getResource("spacecafe.conf").file))
        }
    }
}
