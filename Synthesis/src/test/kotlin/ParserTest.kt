import com.funguscow.synthland.synth.Parser
import com.funguscow.synthland.synth.UniformSampleScale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test

class ParserTest {

    @Test
    fun scalesTestLiteralIntervals() {
        val scale = Parser.parseScale(JsonObject(mapOf(
            "base" to JsonPrimitive("C4"),
            "intervals" to JsonArray(listOf(
                JsonPrimitive(0),
                JsonPrimitive(2),
                JsonPrimitive(4),
                JsonPrimitive(5),
                JsonPrimitive(7),
                JsonPrimitive(9),
                JsonPrimitive(11),
            ))
        )))
        val expected = UniformSampleScale(listOf(
            60.0, 62.0, 64.0, 65.0, 67.0, 69.0, 71.0
        ))
        assert(scale == expected) {"$scale did not match $expected"}
    }

    @Test
    fun scalesTestStrIntervals() {
        val scale = Parser.parseScale(JsonObject(mapOf(
            "base" to JsonPrimitive("A4"),
            "intervals" to JsonArray(listOf(
                JsonPrimitive("I"),
                JsonPrimitive("II"),
                JsonPrimitive("iii"),
                JsonPrimitive("IV"),
                JsonPrimitive("V"),
                JsonPrimitive("vi"),
                JsonPrimitive("vii"),
            ))
        )))
        val expected = UniformSampleScale(listOf(
            69.0, 71.0, 72.0, 74.0, 76.0, 77.0, 79.0
        ))
        assert(scale == expected) {"$scale did not match $expected"}
    }

    @Test
    fun scalesTestLiteralArray() {
        val notes = listOf(
            40.0, 42.0, 49.0, 69.0, 80.0
        )
        val scale = Parser.parseScale(JsonArray(notes.map(::JsonPrimitive)))
        val expected = UniformSampleScale(notes)
        assert(scale == expected) {"$scale did not match $expected"}
    }

    @Test
    fun scalesTestNotes() {
        val scale = Parser.parseScale(JsonArray(listOf(
            JsonPrimitive("D2"),
            JsonPrimitive("Fb2"),
            JsonPrimitive("A#2"),
            JsonPrimitive("C3"),
            JsonPrimitive("G#4")
        )))
        val expected = UniformSampleScale(listOf(
            38.0, 40.0, 46.0, 48.0, 68.0
        ))
        assert(scale == expected) {"$scale did not match $expected"}
    }

    @Test
    fun scalesTestShorthandImplicit() {
        val scale = Parser.parseScale(JsonPrimitive("C4"))
        val expected = UniformSampleScale(listOf(
            60.0, 62.0, 64.0, 65.0, 67.0, 69.0, 71.0
        ))
        assert(scale == expected) {"$scale did not match $expected"}
    }

    @Test
    fun scalesTestShorthandExplicit() {
        val scale = Parser.parseScale(JsonPrimitive("C#5h"))
        val expected = UniformSampleScale(listOf(
            73.0, 75.0, 76.0, 78.0, 80.0, 81.0, 84.0
        ))
        assert(scale == expected) {"$scale did not match $expected"}
    }
}
