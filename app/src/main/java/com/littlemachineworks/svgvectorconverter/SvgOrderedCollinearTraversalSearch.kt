package com.littlemachineworks.svgvectorconverter

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.random.Random

/** Android-free parallel entry point for the G3.11 ordered collinear traversal investigation. */
internal object SvgOrderedCollinearTraversalSearch {
    data class Progress(
        val completedCases: Int,
        val totalCases: Int,
        val workerCount: Int,
        val perSeedProcessed: List<Int>
    ) {
        val percentComplete: Double
            get() = if (totalCases <= 0) 100.0 else completedCases.toDouble() * 100.0 / totalCases.toDouble()
    }

    private data class SeedResult(
        val generated: Int,
        val valid: Int,
        val rejected: Int,
        val changed: Int,
        val safeChanges: Int,
        val unsafeChanges: Int,
        val endpointChanges: Int,
        val traversalOrderViolations: Int,
        val traveledLengthChanges: Int,
        val reversalCases: Int,
        val zeroLengthCases: Int,
        val consolidatedSegments: Int,
        val witnesses: List<SvgPathDataOptimizer.OrderedCollinearTraversalDiagnostic>,
        val elapsedNanos: Long
    )

    private val survivorSources = listOf(
        "M921359,-100000V0.001L-0.5,0.5L0.015625,-0.015625L-5603.68,0.00390625C0.0625,-3865.8,0.015625,-4.29581,5,0.43125Q-100000,5.44538,346409,-0.863562V-0.0625V1A100000,0.00390625,180,0,1,-0.25,100000V667.398",
        "M-99,-0.125L0.25,34916.6Q4236.92,0.932262,-100,-0.5A0.0625,0.03125,90,0,1,865822,-0.412029H-4678.91A100,0.0625,180,0,0,99,-10H0.5H0",
        "M-1,-0.5L-0.015625,-10C10,-10,-99,-99,0.001,0.5A999,916.159,0,1,0,5,0.125L0.25,-0.5C-90429.8,-767.512,2,-142.946,-299.795,-570334A0.125,0.015625,360,1,1,-99,0.03125V999V99V-1",
        "M0.0625,100L999,128456H-10H-3198.81Q63482.6,0.125,0.0625,100000Q-2,399.738,784.595,-0.001A100,0.5,360,0,0,0.125,1000000V0.015625",
        "M5168,-427752V-3.5468L-1000000,-5Q-205.28,-5,9.08038,0.00390625ZM-253.962,0L0.00390625,2Q-357.629,0.0625,4784.1,2V-999L0.0625,-100C-999,-1,268.2,-0.015625,-5,0.00390625H0.03125H1000000A0.316118,5,45,1,0,-10,-10V-1A1,4.34564,270,1,1,-1000000,0.0625"
    )

    fun runDefault(progressCallback: ((Progress) -> Unit)? = null): String = run(
        casesPerSeed = 25_000,
        seeds = listOf(0x6316_2026L, 0x6316_0001L, 0x6316_0002L, 0x1D40_2026L),
        progressCallback = progressCallback
    )

    fun run(
        casesPerSeed: Int,
        seeds: List<Long>,
        progressCallback: ((Progress) -> Unit)? = null
    ): String {
        require(casesPerSeed >= 0)
        require(seeds.isNotEmpty())
        val started = System.nanoTime()

        val survivorDiagnostics = survivorSources.map {
            SvgPathDataOptimizer.diagnoseOrderedCollinearTraversal(it, productionReplay = true)
        }

        val totalCases = casesPerSeed * seeds.size
        val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val workers = minOf(4, seeds.size, processors)
        val perSeed = AtomicIntegerArray(seeds.size)
        val executor = Executors.newFixedThreadPool(workers)

        val futures = seeds.mapIndexed { index, seed ->
            executor.submit<SeedResult> {
                val random = Random(seed)
                val witnesses = mutableListOf<SvgPathDataOptimizer.OrderedCollinearTraversalDiagnostic>()
                var generated = 0
                var valid = 0
                var rejected = 0
                var changed = 0
                var safe = 0
                var unsafe = 0
                var endpoint = 0
                var traversal = 0
                var length = 0
                var reversals = 0
                var zeroes = 0
                var consolidated = 0
                val seedStarted = System.nanoTime()

                repeat(casesPerSeed) { caseIndex ->
                    generated++
                    val source = generateTargetedPath(random, caseIndex)
                    try {
                        val d = SvgPathDataOptimizer.diagnoseOrderedCollinearTraversal(source, productionReplay = false)
                        if (!d.parseable) {
                            rejected++
                        } else {
                            valid++
                            if (d.reversalPairsBefore > 0) reversals++
                            if (d.zeroLengthLinesBefore > 0) zeroes++
                            consolidated += d.consolidatedSegments
                            if (d.changed) {
                                changed++
                                if (d.safe) safe++ else {
                                    unsafe++
                                    if (!d.endpointsPreserved) endpoint++
                                    if (!d.orderedTraversalPreserved) traversal++
                                    if (!d.traveledLengthPreserved) length++
                                    if (witnesses.size < 32) witnesses += d
                                }
                            }
                        }
                    } catch (_: Throwable) {
                        rejected++
                    }
                    val processed = caseIndex + 1
                    if (processed == casesPerSeed || processed % 250 == 0) {
                        perSeed.set(index, processed)
                        progressCallback?.invoke(
                            Progress(
                                completedCases = List(seeds.size) { perSeed.get(it) }.sum(),
                                totalCases = totalCases,
                                workerCount = workers,
                                perSeedProcessed = List(seeds.size) { perSeed.get(it) }
                            )
                        )
                    }
                }
                SeedResult(generated, valid, rejected, changed, safe, unsafe, endpoint, traversal, length,
                    reversals, zeroes, consolidated, witnesses, System.nanoTime() - seedStarted)
            }
        }

        val results = try { futures.map { it.get() } } finally { executor.shutdownNow() }
        val survivorUnsafe = survivorDiagnostics.count { !it.safe }
        val valid = results.sumOf { it.valid }
        val rejected = results.sumOf { it.rejected }
        val changed = results.sumOf { it.changed }
        val safe = results.sumOf { it.safeChanges }
        val unsafe = results.sumOf { it.unsafeChanges }
        val endpoint = results.sumOf { it.endpointChanges }
        val traversal = results.sumOf { it.traversalOrderViolations }
        val length = results.sumOf { it.traveledLengthChanges }
        val reversalCases = results.sumOf { it.reversalCases }
        val zeroLengthCases = results.sumOf { it.zeroLengthCases }
        val consolidated = results.sumOf { it.consolidatedSegments }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.11 automated ordered collinear traversal-safety stress search")
            appendLine()
            appendLine("G3.10 survivor replays: ${survivorDiagnostics.size}")
            appendLine("Unsafe survivor replays: $survivorUnsafe")
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workers")
            appendLine("Available processors: $processors")
            appendLine("Valid targeted cases: $valid")
            appendLine("Rejected targeted cases: $rejected")
            appendLine("Collinear stage changed: $changed")
            appendLine("Safe monotonic consolidations: $safe")
            appendLine("Unsafe consolidations: $unsafe")
            appendLine("Endpoint changes: $endpoint")
            appendLine("Traversal-order violations: $traversal")
            appendLine("Traveled-length changes: $length")
            appendLine("Cases containing reversals/backtracking: $reversalCases")
            appendLine("Cases containing zero-length lines: $zeroLengthCases")
            appendLine("Total line segments consolidated: $consolidated")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            appendLine()
            if (survivorUnsafe > 0 || unsafe > 0) {
                appendLine("RESULT: G3.11 found an ordered-traversal safety violation.")
                appendLine("Recommendation: keep production unchanged and repair the collinear consolidator before revisiting G3.7 convergence.")
            } else {
                appendLine("RESULT: all five G3.10 survivors and all targeted stress cases preserved exact ordered traversal.")
                appendLine("Recommendation: treat the remaining G3.10 direct-collinear signal as comparator endpoint/length bookkeeping, repair that diagnostic comparator, then rerun G3.7 before any production convergence change.")
            }

            appendLine()
            appendLine("────────────────────────────────")
            appendLine("G3.10 survivor replay classification")
            survivorDiagnostics.forEachIndexed { i, d ->
                appendLine()
                appendLine("${i + 1}. Survivor ${i + 1}")
                appendLine("   Changed by collinear stage: ${d.changed}")
                appendLine("   Safe: ${d.safe}")
                appendLine("   Endpoints preserved: ${d.endpointsPreserved}")
                appendLine("   Ordered traversal preserved: ${d.orderedTraversalPreserved}")
                appendLine("   Traveled length preserved: ${d.traveledLengthPreserved}")
                appendLine("   Reversal pairs before: ${d.reversalPairsBefore}")
                appendLine("   Reason: ${d.reason}")
                appendLine("   Before collinear consolidation: ${d.preCollinear}")
                appendLine("   After collinear consolidation: ${d.postCollinear}")
                if (!d.safe) {
                    appendLine("   Before traversal signature: ${d.beforeTraversalSignature}")
                    appendLine("   After traversal signature: ${d.afterTraversalSignature}")
                }
            }

            val unsafeWitnesses = results.flatMap { it.witnesses }
            if (unsafeWitnesses.isNotEmpty()) {
                appendLine()
                appendLine("────────────────────────────────")
                appendLine("Unsafe targeted witnesses")
                unsafeWitnesses.take(64).forEachIndexed { i, d ->
                    appendLine()
                    appendLine("${i + 1}. ${d.reason}")
                    appendLine("   Source: ${d.source}")
                    appendLine("   Endpoints preserved: ${d.endpointsPreserved}")
                    appendLine("   Ordered traversal preserved: ${d.orderedTraversalPreserved}")
                    appendLine("   Traveled length preserved: ${d.traveledLengthPreserved}")
                    appendLine("   Reversal pairs before: ${d.reversalPairsBefore}")
                    appendLine("   Zero-length lines before: ${d.zeroLengthLinesBefore}")
                    appendLine("   Before: ${d.preCollinear}")
                    appendLine("   After: ${d.postCollinear}")
                    appendLine("   Before traversal signature: ${d.beforeTraversalSignature}")
                    appendLine("   After traversal signature: ${d.afterTraversalSignature}")
                }
            }
        }
    }

    private fun generateTargetedPath(random: Random, caseIndex: Int): String {
        fun r(min: Int, max: Int) = random.nextInt(min, max + 1)
        val x = r(-5000, 5000)
        val y = r(-5000, 5000)
        val step = r(1, 200)
        val step2 = r(1, 200)
        return when (caseIndex % 8) {
            0 -> "M$x,$y H${x + step} H${x + step + step2} H${x + step + step2 + step}"
            1 -> "M$x,$y V${y - step} V${y - step - step2} V${y - step - step2 - step}"
            2 -> "M$x,$y L${x + step},${y + step} L${x + step + step2},${y + step + step2} L${x + step + step2 + step},${y + step + step2 + step}"
            3 -> "M$x,$y H${x + step + step2} H${x + step} H${x + step + step2 + step}"
            4 -> "M$x,$y V${y + step + step2} V${y + step} V${y + step + step2 + step}"
            5 -> "M$x,$y L${x + step},${y + step} L${x + step},${y + step} L${x + step + step2},${y + step + step2}"
            6 -> "M$x,$y H${x + step} H${x + step + step2} M${-x},${-y} V${-y - step} V${-y - step - step2}"
            else -> "M$x,$y H${x + step} L${x + step + step2},${y + step2} V${y + step2 + step} L${x + step + step2 + step},${y + step2 + step + step}"
        }
    }
}
