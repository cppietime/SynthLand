package com.funguscow.synthland.synth

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.serialization.json.internal.FormatLanguage
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlin.math.PI

typealias ComponentBuilder = (JsonObject, MutableMap<String, Component>) -> Component

object Parser {
    private val parsers = mapOf<String, ComponentBuilder>(
        Pair("linear", ::buildLinear),
        Pair("modifier", ::buildModifier),
        Pair("sin", ::buildSin),
        Pair("saw", ::buildSaw),
        Pair("square", ::buildSquare),
        Pair("noise", ::buildNoise),
        Pair("supersaw", ::buildSuperSaw),
        Pair("add", ::buildAddition),
        Pair("multiply", ::buildMultiplication),
        Pair("apply", ::buildApply),
        Pair("ears", ::buildEars),
        Pair("pan", ::buildPan),
        Pair("pluck", ::buildKarplusStrong),
        Pair("binbeat", ::buildBinBeat),
        Pair("monobeat", ::buildMonoBeat),
        Pair("isotone", ::buildIsoTone),

        Pair("chain", ::buildChain),
        Pair("iir", ::buildIir),
        Pair("biquad", ::buildBiquad),
        Pair("fir", ::buildFir),
        Pair("each", ::buildEach),
        Pair("scale", ::buildScale),
        Pair("design_iir", ::designIir),
        Pair("firwin", ::windowFir),
        Pair("apply_pan", ::buildApplyPan),
        Pair("lfo_delay", ::buildLfoDelay),
        Pair("chorus", ::buildChorus),

        Pair("copy_generator", ::buildCopyGen),
        Pair("copy_filter", ::buildCopyFilter),

        Pair("adsr", ::buildAdsr),

        Pair("load", ::buildFromFile),
    )

    fun parseVoice(json: JsonObject) : Voice {
        val instrument = parseInstrument(json["instrument"]!!.jsonObject)
        val volume = json["volume"]?.jsonPrimitive?.double ?: 1.0
        val speed = json["speed"]?.jsonPrimitive?.double ?: 1.0
        val scale = parseScale(json["scale"]!!.jsonObject)
        val type = NoteType.valueOf(json["type"]?.jsonPrimitive?.content ?: NoteType.DRONE.name)
        return Voice(instrument, volume, speed, scale, type)
    }

    fun parseScale(json: JsonObject) : NoteScale {
        // TODO: Customize, for now just use major
        return UniformRandomScale(69.0, listOf(2, 2, 1, 2, 2, 2, 1))
    }

    @OptIn(InternalSerializationApi::class)
    fun parseInstrument(@FormatLanguage("json", "", "") str: String) : Instrument {
        val json = Json.decodeFromString<JsonObject>(str)
        return parseInstrument(json)
    }

    fun parseInstrument(json: JsonObject) : Instrument {
        val namespace = mutableMapOf<String, Component>()
        val root = buildComponent(json["instrument"]!!.jsonObject, namespace) as Generator
        namespace.values.forEach {
            when (it) {
                is CopyFilter -> {it.pointer = namespace[it.name]!! as Filter}
                is CopyGenerator -> {it.pointer = namespace[it.name]!! as Generator}
            }
        }
        val envelope = json["envelope"]?.let{buildComponent(it.jsonObject, namespace)} as Envelope? ?: NoEnvelope()
        return Instrument(root, namespace, envelope)
    }

    fun buildComponent(json: JsonObject, namespace: MutableMap<String, Component>): Component {
        val type = json["type"]!!.jsonPrimitive.content
        val parser = parsers[type] ?: throw IllegalArgumentException("Parser type $type does not exist")
        val component = parser(json, namespace)
        json["name"]?.also {namespace[it.jsonPrimitive.content] = component}
        return component
    }
    fun readGenerator(json: JsonObject, namespace: MutableMap<String, Component>): Generator {
        val generatorMap = json["generator"] as JsonObject?
        return generatorMap?.let {buildComponent(it, namespace) as Generator} ?: Linear()
    }

    fun buildLinear(json: JsonObject, namespace: MutableMap<String, Component>): Linear = Linear()
    fun buildModifier(json: JsonObject, namespace: MutableMap<String, Component>): NoteModifier {
        val generator = readGenerator(json, namespace)
        val frequency = json["frequency"]?.jsonPrimitive?.float?.toDouble()
        val freqMul = json["frequency_multiplier"]?.jsonPrimitive?.float?.toDouble()
        val freqAdd = json["frequency_addition"]?.jsonPrimitive?.float?.toDouble()
        val volume = json["volume"]?.jsonPrimitive?.float?.toDouble()
        val volMul = json["volume_multiplier"]?.jsonPrimitive?.float?.toDouble()
        return NoteModifier(generator, frequency = frequency, frequencyMultiplier = freqMul, frequencyAddition = freqAdd, volume = volume, volumeMultiplier = volMul)
    }
    fun buildSin(json: JsonObject, namespace: MutableMap<String, Component>): SineWave {
        val generator = readGenerator(json, namespace)
        return SineWave(generator)
    }
    fun buildSaw(json: JsonObject, namespace: MutableMap<String, Component>): SawWave {
        val generator = readGenerator(json, namespace)
        return SawWave(generator)
    }
    fun buildSquare(json: JsonObject, namespace: MutableMap<String, Component>): SquareWave {
        val generator = readGenerator(json, namespace)
        val duty = json["duty"]?.jsonPrimitive?.double ?: 0.5
        return SquareWave(generator, duty)
    }
    fun buildNoise(json: JsonObject, namespace: MutableMap<String, Component>): WhiteNoise = WhiteNoise()
    fun buildSuperSaw(json: JsonObject, namespace: MutableMap<String, Component>): SuperSaw {
        val generator = readGenerator(json, namespace)
        val detunes = json["detunes"]!!.jsonArray.map {it.jsonPrimitive.float.toDouble()}.toDoubleArray()
        val scales = json["scales"]?.jsonArray?.map{it.jsonPrimitive.float.toDouble()}?.toDoubleArray() ?: doubleArrayOf()
        return SuperSaw(detunes, generator, scales)
    }
    fun buildAddition(json: JsonObject, namespace: MutableMap<String, Component>): Addition {
        val left = buildComponent(json["left"]!!.jsonObject, namespace) as Generator
        val right = buildComponent(json["right"]!!.jsonObject, namespace) as Generator
        return Addition(left, right)
    }
    fun buildMultiplication(json: JsonObject, namespace: MutableMap<String, Component>): Multiplication {
        val left = buildComponent(json["left"]!!.jsonObject, namespace) as Generator
        val right = buildComponent(json["right"]!!.jsonObject, namespace) as Generator
        return Multiplication(left, right)
    }
    fun buildApply(json: JsonObject, namespace: MutableMap<String, Component>): Apply {
        val generator = readGenerator(json, namespace)
        val filter = buildComponent(json["filter"]!!.jsonObject, namespace) as Filter
        return Apply(filter, generator)
    }
    fun buildEars(json: JsonObject, namespace: MutableMap<String, Component>): Ears {
        val left = json["left"]?.let { buildComponent(it.jsonObject, namespace) } as Generator?
        val right = json["right"]?.let { buildComponent(it.jsonObject, namespace) } as Generator?
        return Ears(left, right)
    }
    fun buildPan(json: JsonObject, namespace: MutableMap<String, Component>): Pan {
        val generator = readGenerator(json, namespace)
        val left = json["left"]!!.jsonPrimitive.float.toDouble()
        val right = json["right"]!!.jsonPrimitive.float.toDouble()
        return Pan(generator, left, right)
    }
    fun buildKarplusStrong(json: JsonObject, namespace: MutableMap<String, Component>): KarplusStrongGenerator {
        val generator = readGenerator(json, namespace)
        val decay = rpnToDoubleTransformer(json["decay"]) ?: {1.0}
        val stretch = rpnToDoubleTransformer(json["stretch"]) ?: {1.0}
        val drum = json["drum"]?.jsonPrimitive?.float?.toDouble() ?: 0.0
        return KarplusStrongGenerator(generator, decay, stretch, drum)
    }

    fun buildBinBeat(json: JsonObject, namespace: MutableMap<String, Component>): Ears {
        val left = readGenerator(json, namespace)
        val right = readGenerator(json, namespace)
        val frequency = json["frequency"]?.jsonPrimitive?.float?.toDouble() ?: 0.0
        val filteredRight = NoteModifier(right, frequencyAddition = frequency)
        return Ears(left, filteredRight)
    }

    fun buildMonoBeat(json: JsonObject, namespace: MutableMap<String, Component>): Addition {
        val left = readGenerator(json, namespace)
        val filteredLeft = NoteModifier(left, volumeMultiplier = 0.5)
        val right = readGenerator(json, namespace)
        val frequency = json["frequency"]?.jsonPrimitive?.float?.toDouble() ?: 0.0
        val filteredRight = NoteModifier(right, frequencyAddition = frequency, volumeMultiplier = 0.5)
        return Addition(filteredLeft, filteredRight)
    }

    fun buildIsoTone(json: JsonObject, namespace: MutableMap<String, Component>): Multiplication {
        val tone = readGenerator(json, namespace)
        val square = buildSquare(json["square"]!!.jsonObject, namespace)
        val frequency = json["frequency"]?.jsonPrimitive?.float?.toDouble() ?: 1.0
        val squareFiltered = NoteModifier(square, frequency = frequency, volume = 0.5)
        json["name"]?.jsonPrimitive?.content?.also {
            namespace["${it}_square_filtered"] = squareFiltered
        }
        val offset = DC(0.5)
        val squareOffset = Addition(squareFiltered, offset)
        return Multiplication(tone, squareOffset)
    }

    fun rpnToDoubleTransformer(json: JsonElement?): ((Double) -> Double)? {
        if (json == null) {
            return null
        }
        val rpn = json.jsonArray.map {elem ->
            elem.jsonPrimitive.content
        }
        return {it ->
            val stack = mutableListOf<Double>()
            rpn.forEach {elem ->
                when (elem) {
                    "x" -> stack.add(it)
                    "+", "-", "*", "/" -> {
                        val right = stack.removeLast()
                        val left = stack.removeLast()
                        stack.add(when (elem) {
                            "+" -> left + right
                            "-" -> left - right
                            "*" -> left - right
                            "/" -> left - right
                            else -> throw Exception() // Unreachable
                        })
                    }
                    else -> stack.add(elem.toDouble())
                }
            }
            stack.first()
        }
    }

    fun buildChain(json: JsonObject, namespace: MutableMap<String, Component>): Chain {
        val list = json["filters"]!!.jsonArray
        val filters = list.map { buildComponent(it.jsonObject, namespace) as Filter }
        return Chain(*filters.toTypedArray())
    }
    fun buildIir(json: JsonObject, namespace: MutableMap<String, Component>): IirFilter {
        val a = json["a"]?.let {lst -> lst.jsonArray.map {elem -> elem.jsonPrimitive.float.toDouble()}}?.toDoubleArray() ?: doubleArrayOf(1.0)
        val b = json["b"]?.let {lst -> lst.jsonArray.map {elem -> elem.jsonPrimitive.float.toDouble()}}?.toDoubleArray() ?: doubleArrayOf(1.0)
        return IirFilter(a, b)
    }
    fun buildFir(json: JsonObject, namespace: MutableMap<String, Component>): FirFilter {
        val coefficients = json["coefficients"]?.let {lst -> lst.jsonArray.map {elem -> elem.jsonPrimitive.float.toDouble()}}?.toDoubleArray() ?: doubleArrayOf(1.0)
        return FirFilter(coefficients)
    }
    fun buildBiquad(json: JsonObject, namespace: MutableMap<String, Component>): BiquadFilter {
        val a = json["a"]?.let {lst -> lst.jsonArray.map {elem -> elem.jsonPrimitive.float.toDouble()}}?.toDoubleArray() ?: doubleArrayOf(1.0, 0.0, 0.0)
        val b = json["b"]?.let {lst -> lst.jsonArray.map {elem -> elem.jsonPrimitive.float.toDouble()}}?.toDoubleArray() ?: doubleArrayOf(1.0, 0.0, 0.0)
        return BiquadFilter(a, b)
    }
    fun buildEach(json: JsonObject, namespace: MutableMap<String, Component>): ApplyEach {
        val filters = json["filters"]!!.let {lst -> lst.jsonArray.map { elem -> buildComponent(elem.jsonObject, namespace) as Filter }}.toTypedArray()
        return ApplyEach(*filters)
    }
    fun buildScale(json: JsonObject, namespace: MutableMap<String, Component>): Scale {
        val scale = json["scale"]!!.jsonPrimitive.float.toDouble()
        return Scale(scale)
    }
    fun designIir(json: JsonObject, namespace: MutableMap<String, Component>): Filter {
        val prototype = json["prototype"]!!.jsonPrimitive.content.let(PrototypeTypes::valueOf).function
        val isBiquad = json["biquads"]?.jsonPrimitive?.boolean ?: true
        val filterType = json["filter"]?.jsonPrimitive?.content?.let(FilterType::valueOf) ?: FilterType.LOWPASS
        val degree = json["degree"]!!.jsonPrimitive.int
        val cutoff =  parseCutoffFreq(json["cutoff"]!!)
        val second = json["secondaryCutoff"]?.let(::parseCutoffFreq)
        val (poles, zeros, gain) = IirFilter.digitalPZK(prototype, degree, filterType, cutoff, second)

        if (isBiquad) {
            val filters = BiquadFilter.fromPZKs(poles, zeros, gain)
            return Chain(*filters.toTypedArray())
        }
        return IirFilter.fromPZK(poles, zeros, gain)
    }
    fun windowFir(json: JsonObject, namespace: MutableMap<String, Component>): FirFilter {
        val degree = json["degree"]!!.jsonPrimitive.int
        val window = json["window"]?.jsonPrimitive?.content?.let(WindowFuncType::valueOf)?.function ?: WindowFuncs::boxcarWindow
        val cutoffs = json["cutoffs"]!!.jsonArray.map(::parseCutoffFreq).toDoubleArray()
        return FirFilter.windowed(degree, cutoffs, window)
    }
    fun buildApplyPan(json: JsonObject, namespace: MutableMap<String, Component>): ApplyPan {
        val left = buildComponent(json["left"]!!.jsonObject, namespace) as Filter
        val right = buildComponent(json["right"]!!.jsonObject, namespace) as Filter
        return ApplyPan(left, right)
    }
    fun buildLfoDelay(json: JsonObject, namespace: MutableMap<String, Component>): LfoDelay {
        val frequency = json["frequency"]!!.jsonPrimitive.float.toDouble()
        val depth = json["depth"]!!.jsonPrimitive.float.toDouble()
        return LfoDelay(frequency, depth)
    }
    fun buildChorus(json: JsonObject, namespace: MutableMap<String, Component>): Chorus {
        if ("delays" in json) {
            val delays = json["delays"]!!.jsonArray.map { triple ->
                triple.jsonArray.map { it.jsonPrimitive.float.toDouble() }.let { Triple(it[0], it[1], it[2]) }
            }
            return Chorus(delays.toTypedArray())
        }
        val frequency = json["frequency"]!!.jsonPrimitive.float.toDouble()
        val depth = json["depth"]!!.jsonPrimitive.float.toDouble()
        val size = json["size"]!!.jsonPrimitive.int
        return Chorus.uniform(frequency, depth, size)
    }

    fun buildCopyGen(json: JsonObject, namespace: MutableMap<String, Component>): CopyGenerator = CopyGenerator(json["copies"]!!.jsonPrimitive.content)
    fun buildCopyFilter(json: JsonObject, namespace: MutableMap<String, Component>): CopyFilter = CopyFilter(json["copies"]!!.jsonPrimitive.content)

    /**
     * Returns angular frequency in [0, PI]
     */
    fun parseCutoffFreq(json: JsonElement): Double {
        when(json) {
            is JsonPrimitive -> {
                // Hz with sampling rate
                if (json.content.contains('/')) {
                    return json.content.split('/').let{it[0].toDouble() * 2 * PI / it[1].toDouble()}
                }
                // Fraction of nyquist
                return json.float.toDouble() * PI
            }
            is JsonObject -> {
                if ("hz" in json) {
                    val hz = json["hz"]!!.jsonPrimitive.float.toDouble()
                    val sampling = json["sampling"]!!.jsonPrimitive.float.toDouble()
                    return hz / sampling * 2 * PI
                }
                if ("angular" in json) {
                    return json["angular"]!!.jsonPrimitive.float.toDouble()
                }
                if ("of_nyquist" in json) {
                    return json["of_nyquist"]!!.jsonPrimitive.float.toDouble() * PI
                }
                throw IllegalArgumentException("Invalid frequency $json")
            }
            else -> throw IllegalArgumentException("Invalid frequency $json")
        }
    }

    fun buildFromFile(json: JsonObject, namespace: MutableMap<String, Component>) : Component {
        val path = json["path"]!!.jsonPrimitive.content
        val stream = FileInputStream(path)
        return stream.use { buildComponent(Json.decodeFromStream(it), namespace) }
    }

    fun buildAdsr(json: JsonObject, namespace: MutableMap<String, Component>): ADSR {
        val attack = json["attack"]!!.jsonPrimitive.float.toDouble()
        val decay = json["decay"]!!.jsonPrimitive.float.toDouble()
        val sustain = json["sustain"]!!.jsonPrimitive.float.toDouble()
        val release = json["release"]!!.jsonPrimitive.float.toDouble()
        return ADSR(attack, decay, sustain, release)
    }
}

fun main() {
    var x = 1
    var y = 2
    x = y.also{y = x}
    println("$x - $y")
}
