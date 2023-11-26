import com.simplecityapps.localmediaprovider.local.provider.parseDate
import junit.framework.TestCase

class AudioFileExtKtTest : TestCase() {
    fun testParseDate() {
        assertEquals("2004", "2004".parseDate())
        assertEquals("2010", "2010-00-00".parseDate())
        assertEquals("2020", "2020-04-03T07:00:00Z".parseDate())
    }
}
