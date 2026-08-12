package com.example.svgvectorconverter

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/** Android-free parallel entry point for the G3.7 post-serialization geometry convergence search. */
object SvgPostSerializationGeometryConvergenceSearch {

    data class Progress(
        val completedCases: Int,
        val totalCases: Int,
        val workerCount: Int,
        val perSeedProcessed: List<Int>,
        val casesPerSeed: Int
    ) {
        val percentComplete: Double
            get() = if (totalCases <= 0) 100.0
            else completedCases.toDouble() * 100.0 / totalCases.toDouble()
    }

    fun runDefault(
        progressCallback: ((Progress) -> Unit)? = null
    ): String = run(
        casesPerSeed = 25_000,
        // Deliberately reuse the exact G3.6 corpus so the 350 unresolved
        // verification cases can be compared directly.
        seeds = listOf(
            0x6316_2026L,
            0x6316_0001L,
            0x6316_0002L,
            0x1D40_2026L
        ),
        progressCallback = progressCallback
    )

    fun run(
        casesPerSeed: Int,
        seeds: List<Long>,
        progressCallback: ((Progress) -> Unit)? = null
    ): String {
        require(casesPerSeed >= 0) { "casesPerSeed must be non-negative" }
        require(seeds.isNotEmpty()) { "At least one seed is required" }

        val started = System.nanoTime()
        val totalCases = casesPerSeed * seeds.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val workerCount = minOf(4, seeds.size, availableProcessors)
        val perSeedProgress = AtomicIntegerArray(seeds.size)
        val executor = Executors.newFixedThreadPool(workerCount)

        val futures = seeds.mapIndexed { index, seed ->
            executor.submit<SvgPathDataOptimizer.PostSerializationGeometryConvergenceResult> {
                SvgPathDataOptimizer.runPostSerializationGeometryConvergenceStressSearch(
                    caseCount = casesPerSeed,
                    seed = seed,
                    maximumWitnesses = 5,
                    progressCallback = { currentSeedProcessed ->
                        perSeedProgress.set(index, currentSeedProcessed)
                        if (progressCallback != null) {
                            val snapshot = List(seeds.size) { seedIndex ->
                                perSeedProgress.get(seedIndex)
                            }
                            progressCallback.invoke(
                                Progress(
                                    completedCases = snapshot.sum(),
                                    totalCases = totalCases,
                                    workerCount = workerCount,
                                    perSeedProcessed = snapshot,
                                    casesPerSeed = casesPerSeed
                                )
                            )
                        }
                    }
                )
            }
        }

        val results = try {
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val valid = results.sumOf { it.validCases }
        val rejected = results.sumOf { it.rejectedGeneratedCases }
        val unchanged = results.sumOf { it.unchangedByCandidate }
        val changed = results.sumOf { it.changedByCandidate }
        val redundantChanged = results.sumOf { it.redundantGeometryChangedCases }
        val collinearChanged = results.sumOf { it.collinearGeometryChangedCases }
        val matchedIndependent = results.sumOf { it.matchedIndependentSecondPass }
        val differedIndependent = results.sumOf { it.differedFromIndependentSecondPass }
        val fixed = results.sumOf { it.fixedAfterCandidate }
        val stillChanged = results.sumOf { it.stillChangedAfterVerification }
        val sampledGeometryMismatches = results.sumOf { it.sampledGeometryMismatchCount }
        val saved = results.sumOf { it.totalCharactersSaved }
        val grown = results.sumOf { it.totalCharactersGrown }
        val candidateNanos = results.sumOf { it.candidateNanos }
        val verificationNanos = results.sumOf { it.verificationNanos }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.7 automated post-serialization geometry convergence differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("Unchanged by G3.7 candidate: $unchanged")
            appendLine("Changed by G3.7 candidate: $changed")
            appendLine("Redundant-geometry stage changed: $redundantChanged")
            appendLine("Collinear-consolidation stage changed: $collinearChanged")
            appendLine("Matched independent full pass 2: $matchedIndependent")
            appendLine("Differed from independent full pass 2: $differedIndependent")
            appendLine("Fixed after G3.7 candidate: $fixed")
            appendLine("Still changed after verification: $stillChanged")
            appendLine("Sampled geometry mismatches: $sampledGeometryMismatches")
            appendLine("Characters saved by G3.7 candidate: $saved")
            appendLine("Characters added by G3.7 candidate: $grown")
            appendLine("Candidate CPU time: " + String.format(java.util.Locale.US, "%.2f ms", candidateNanos / 1_000_000.0))
            appendLine("Full verification CPU time: " + String.format(java.util.Locale.US, "%.2f ms", verificationNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            appendLine()
            when {
                sampledGeometryMismatches > 0 -> {
                    appendLine("RESULT: G3.7 produced sampled geometry mismatches.")
                    appendLine("Recommendation: reject the candidate and investigate every geometry witness.")
                }
                stillChanged > 0 -> {
                    appendLine("RESULT: the G3.7 candidate did not reach a fixed point for every case.")
                    appendLine("Recommendation: keep production unchanged and inspect the remaining verification witnesses.")
                }
                differedIndependent > 0 -> {
                    appendLine("RESULT: the G3.7 candidate was fixed but did not exactly reproduce every independent second-pass result.")
                    appendLine("Recommendation: keep production unchanged until the remaining spelling differences are classified.")
                }
                valid > 0 -> {
                    appendLine("RESULT: the G3.7 candidate exactly reproduced the independent second pass and remained fixed under full verification across every seed.")
                    appendLine("Recommendation: rerun the locked regression suite, then advance to a guarded VectorDrawable-level production trial.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }

            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("────────────────────────────────")
                appendLine("Seed ${index + 1}")
                append(result.toPlainTextReport())
            }
        }
    }

    fun runG314Default(
        progressCallback: ((Progress) -> Unit)? = null
    ): String = runG314(
        casesPerSeed = 25_000,
        seeds = listOf(
            0x6316_2026L,
            0x6316_0001L,
            0x6316_0002L,
            0x1D40_2026L
        ),
        progressCallback = progressCallback
    )

    fun runG314(
        casesPerSeed: Int,
        seeds: List<Long>,
        progressCallback: ((Progress) -> Unit)? = null
    ): String {
        require(casesPerSeed >= 0) { "casesPerSeed must be non-negative" }
        require(seeds.isNotEmpty()) { "At least one seed is required" }

        val started = System.nanoTime()
        val totalCases = casesPerSeed * seeds.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val workerCount = minOf(4, seeds.size, availableProcessors)
        val perSeedProgress = AtomicIntegerArray(seeds.size)
        val executor = Executors.newFixedThreadPool(workerCount)

        val futures = seeds.mapIndexed { index, seed ->
            executor.submit<SvgPathDataOptimizer.G314PostSerializationGeometryConvergenceResult> {
                SvgPathDataOptimizer.runG314PostSerializationGeometryConvergenceStressSearch(
                    caseCount = casesPerSeed,
                    seed = seed,
                    maximumWitnesses = 5,
                    progressCallback = { currentSeedProcessed ->
                        perSeedProgress.set(index, currentSeedProcessed)
                        if (progressCallback != null) {
                            val snapshot = List(seeds.size) { seedIndex ->
                                perSeedProgress.get(seedIndex)
                            }
                            progressCallback.invoke(
                                Progress(
                                    completedCases = snapshot.sum(),
                                    totalCases = totalCases,
                                    workerCount = workerCount,
                                    perSeedProcessed = snapshot,
                                    casesPerSeed = casesPerSeed
                                )
                            )
                        }
                    }
                )
            }
        }

        val results = try {
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val valid = results.sumOf { it.validCases }
        val rejected = results.sumOf { it.rejectedGeneratedCases }
        val unchanged = results.sumOf { it.unchangedByCandidate }
        val changed = results.sumOf { it.changedByCandidate }
        val redundantChanged = results.sumOf { it.redundantGeometryChangedCases }
        val collinearChanged = results.sumOf { it.collinearGeometryChangedCases }
        val matchedIndependent = results.sumOf { it.matchedIndependentSecondPass }
        val differedIndependent = results.sumOf { it.differedFromIndependentSecondPass }
        val fixed = results.sumOf { it.fixedAfterCandidate }
        val stillChanged = results.sumOf { it.stillChangedAfterVerification }
        val geometryComparisons = results.sumOf { it.geometryComparisons }
        val geometryMismatches = results.sumOf { it.geometryMismatchCount }
        val exactShortCircuits = results.sumOf { it.exactShortCircuitCount }
        val fallbackBidirectional = results.sumOf { it.fallbackBidirectionalCount }
        val comparatorFailures = results.sumOf { it.comparatorFailureCount }
        val saved = results.sumOf { it.totalCharactersSaved }
        val grown = results.sumOf { it.totalCharactersGrown }
        val candidateNanos = results.sumOf { it.candidateNanos }
        val comparatorNanos = results.sumOf { it.comparatorNanos }
        val verificationNanos = results.sumOf { it.verificationNanos }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.14 automated G3.7 convergence-corpus rerun with G3.13 geometry comparator")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("Unchanged by convergence candidate: $unchanged")
            appendLine("Changed by convergence candidate: $changed")
            appendLine("Redundant-geometry stage changed: $redundantChanged")
            appendLine("Collinear-consolidation stage changed: $collinearChanged")
            appendLine("Matched independent full pass 2: $matchedIndependent")
            appendLine("Differed from independent full pass 2: $differedIndependent")
            appendLine("Fixed after convergence candidate: $fixed")
            appendLine("Still changed after verification: $stillChanged")
            appendLine("G3.13 geometry comparisons: $geometryComparisons")
            appendLine("G3.13 geometry mismatches: $geometryMismatches")
            appendLine("Exact ordered-traversal short-circuits: $exactShortCircuits")
            appendLine("Fallback bidirectional comparisons: $fallbackBidirectional")
            appendLine("Comparator failures: $comparatorFailures")
            appendLine("Characters saved by convergence candidate: $saved")
            appendLine("Characters added by convergence candidate: $grown")
            appendLine("Candidate CPU time: " + String.format(java.util.Locale.US, "%.2f ms", candidateNanos / 1_000_000.0))
            appendLine("Comparator CPU time: " + String.format(java.util.Locale.US, "%.2f ms", comparatorNanos / 1_000_000.0))
            appendLine("Full verification CPU time: " + String.format(java.util.Locale.US, "%.2f ms", verificationNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            appendLine()
            when {
                comparatorFailures > 0 -> {
                    appendLine("RESULT: G3.14 encountered diagnostic comparator failures.")
                    appendLine("Recommendation: keep production unchanged and inspect every comparator-failure witness.")
                }
                geometryMismatches > 0 -> {
                    appendLine("RESULT: G3.14 found geometry mismatches under the validated G3.13 comparator.")
                    appendLine("Recommendation: keep production unchanged and inspect every geometry witness before revisiting convergence.")
                }
                stillChanged > 0 -> {
                    appendLine("RESULT: the G3.14 convergence candidate did not reach a fixed point for every case.")
                    appendLine("Recommendation: keep production unchanged and inspect the remaining verification witnesses.")
                }
                differedIndependent > 0 -> {
                    appendLine("RESULT: G3.14 preserved geometry but did not exactly reproduce every independent second-pass result.")
                    appendLine("Recommendation: keep production unchanged until the remaining spelling differences are classified.")
                }
                valid > 0 -> {
                    appendLine("RESULT: G3.14 exactly reproduced the independent second pass, remained fixed, and produced no G3.13 geometry mismatches across every seed.")
                    appendLine("Recommendation: rerun the locked regression suite, then consider a guarded production convergence trial.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }

            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("────────────────────────────────")
                appendLine("Seed ${index + 1}")
                append(result.toPlainTextReport())
            }
        }
    }


    fun runG316Default(
        progressCallback: ((Progress) -> Unit)? = null
    ): String = runG316(
        casesPerSeed = 25_000,
        seeds = listOf(
            0x6316_2026L,
            0x6316_0001L,
            0x6316_0002L,
            0x1D40_2026L
        ),
        progressCallback = progressCallback
    )

    fun runG316(
        casesPerSeed: Int,
        seeds: List<Long>,
        progressCallback: ((Progress) -> Unit)? = null
    ): String {
        require(casesPerSeed >= 0) { "casesPerSeed must be non-negative" }
        require(seeds.isNotEmpty()) { "At least one seed is required" }

        val started = System.nanoTime()
        val totalCases = casesPerSeed * seeds.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val workerCount = minOf(4, seeds.size, availableProcessors)
        val perSeedProgress = AtomicIntegerArray(seeds.size)
        val executor = Executors.newFixedThreadPool(workerCount)

        val futures = seeds.mapIndexed { index, seed ->
            executor.submit<SvgPathDataOptimizer.G316GuardedProductionTrialResult> {
                SvgPathDataOptimizer.runG316GuardedProductionTrialStressSearch(
                    caseCount = casesPerSeed,
                    seed = seed,
                    maximumWitnesses = 5,
                    progressCallback = { currentSeedProcessed ->
                        perSeedProgress.set(index, currentSeedProcessed)
                        if (progressCallback != null) {
                            val snapshot = List(seeds.size) { seedIndex ->
                                perSeedProgress.get(seedIndex)
                            }
                            progressCallback.invoke(
                                Progress(
                                    completedCases = snapshot.sum(),
                                    totalCases = totalCases,
                                    workerCount = workerCount,
                                    perSeedProcessed = snapshot,
                                    casesPerSeed = casesPerSeed
                                )
                            )
                        }
                    }
                )
            }
        }

        val results = try {
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val valid = results.sumOf { it.validCases }
        val rejected = results.sumOf { it.rejectedGeneratedCases }
        val unchanged = results.sumOf { it.candidateUnchanged }
        val changed = results.sumOf { it.candidateChanged }
        val accepted = results.sumOf { it.guardAccepted }
        val guardRejected = results.sumOf { it.guardRejected }
        val unsafeAccepts = results.sumOf { it.unsafeAccepts }
        val falseRejects = results.sumOf { it.falseRejects }
        val acceptedGeometryFailures = results.sumOf { it.acceptedGeometryFailures }
        val acceptedComparatorFailures = results.sumOf { it.acceptedComparatorFailures }
        val acceptedPass2Mismatches = results.sumOf { it.acceptedPass2Mismatches }
        val acceptedNonFixed = results.sumOf { it.acceptedNonFixedCandidates }
        val acceptedValidationFailures = results.sumOf { it.acceptedValidationFailures }
        val coverageDrift = results.sumOf { it.secondPassDriftOutsideCandidateCoverage }
        val geometryComparisons = results.sumOf { it.geometryComparisons }
        val geometryMismatches = results.sumOf { it.geometryMismatchCount }
        val exactShortCircuits = results.sumOf { it.exactShortCircuitCount }
        val fallbackBidirectional = results.sumOf { it.fallbackBidirectionalCount }
        val comparatorFailures = results.sumOf { it.comparatorFailureCount }
        val validationFailures = results.sumOf { it.finalValidationFailures }
        val fixedPointFailures = results.sumOf { it.fixedPointFailures }
        val pass2Mismatches = results.sumOf { it.pass2MismatchCount }
        val saved = results.sumOf { it.totalCharactersSaved }
        val grown = results.sumOf { it.totalCharactersAdded }
        val guardNanos = results.sumOf { it.guardNanos }
        val historicalCoverageExpected = results.mapNotNull { it.expectedHistoricalChangedCandidates }
        val historicalCoverageChecksRequired = historicalCoverageExpected.size
        val historicalCoverageChecksPassed = results.count {
            it.expectedHistoricalChangedCandidates != null && it.historicalCoverageMatched
        }
        val historicalExpectedChangedTotal = historicalCoverageExpected.sum()
        val historicalCoverageMatched =
            historicalCoverageChecksRequired == 0 ||
                historicalCoverageChecksPassed == historicalCoverageChecksRequired
        val rejectionReasons = linkedMapOf<String, Int>()
        results.forEach { result ->
            result.rejectionReasonCounts.forEach { (reason, count) ->
                rejectionReasons[reason] = (rejectionReasons[reason] ?: 0) + count
            }
        }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.16 automated guarded G3.15 shadow-mode stress trial")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("G3.15 candidate unchanged: $unchanged")
            appendLine("G3.15 candidate changed: $changed")
            if (historicalCoverageChecksRequired > 0) {
                appendLine("Historical G3.14 expected changed candidates: $historicalExpectedChangedTotal")
                appendLine("Historical coverage checks passed: $historicalCoverageChecksPassed / $historicalCoverageChecksRequired")
                appendLine("Historical G3.14 coverage reproduced: $historicalCoverageMatched")
            }
            appendLine("Guard accepted: $accepted")
            appendLine("Guard rejected: $guardRejected")
            appendLine("Unsafe accepts: $unsafeAccepts")
            appendLine("False rejects: $falseRejects")
            appendLine("Accepted geometry failures: $acceptedGeometryFailures")
            appendLine("Accepted comparator failures: $acceptedComparatorFailures")
            appendLine("Accepted pass-2 mismatches: $acceptedPass2Mismatches")
            appendLine("Accepted non-fixed candidates: $acceptedNonFixed")
            appendLine("Accepted validation failures: $acceptedValidationFailures")
            appendLine("Second-pass drift outside G3.15 candidate coverage: $coverageDrift")
            appendLine("G3.13 geometry comparisons: $geometryComparisons")
            appendLine("G3.13 geometry mismatches: $geometryMismatches")
            appendLine("Exact ordered-traversal short-circuits: $exactShortCircuits")
            appendLine("Fallback bidirectional comparisons: $fallbackBidirectional")
            appendLine("Comparator failures: $comparatorFailures")
            appendLine("Final validation failures: $validationFailures")
            appendLine("Fixed-point failures: $fixedPointFailures")
            appendLine("Candidate/pass-2 mismatches: $pass2Mismatches")
            appendLine("Characters saved by accepted candidates: $saved")
            appendLine("Characters added by accepted candidates: $grown")
            appendLine("Guard CPU time: " + String.format(java.util.Locale.US, "%.2f ms", guardNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            if (rejectionReasons.isNotEmpty()) {
                appendLine()
                appendLine("Guard rejection reasons")
                rejectionReasons.toSortedMap().forEach { (reason, count) ->
                    appendLine("• $reason: $count")
                }
            }
            appendLine()
            when {
                !historicalCoverageMatched -> {
                    appendLine("RESULT: INVALID TEST — G3.16 did not reproduce the historical G3.14 candidate coverage.")
                    appendLine("Recommendation: do not use this run for production-enablement decisions; repair the harness before continuing.")
                }
                unsafeAccepts > 0 ||
                    acceptedGeometryFailures > 0 ||
                    acceptedComparatorFailures > 0 ||
                    acceptedPass2Mismatches > 0 ||
                    acceptedNonFixed > 0 ||
                    acceptedValidationFailures > 0 -> {
                    appendLine("RESULT: G3.16 found an unsafe accept or accepted-candidate invariant failure.")
                    appendLine("Recommendation: keep G3.15 shadow-only and inspect every failure witness.")
                }
                falseRejects > 0 -> {
                    appendLine("RESULT: G3.16 found false rejects in the G3.15 guard.")
                    appendLine("Recommendation: keep G3.15 shadow-only and classify the false-reject witnesses.")
                }
                valid > 0 -> {
                    appendLine("RESULT: G3.16 found no unsafe accepts, false rejects, or accepted-candidate invariant failures across every seed.")
                    if (coverageDrift > 0) {
                        appendLine("NOTE: second-pass drift existed outside the narrow G3.15 convergence-candidate coverage.")
                        appendLine("Recommendation: inspect the coverage signal before making G3.15 authoritative.")
                    } else {
                        appendLine("Recommendation: combine this with the locked regression suite before the next production-enablement step.")
                    }
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }

            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("────────────────────────────────")
                appendLine("Seed ${index + 1}")
                append(result.toPlainTextReport())
            }
        }
    }


    fun runG317Default(
        progressCallback: ((Progress) -> Unit)? = null
    ): String = runG317(
        casesPerSeed = 25_000,
        seeds = listOf(
            0x6316_2026L,
            0x6316_0001L,
            0x6316_0002L,
            0x1D40_2026L
        ),
        progressCallback = progressCallback
    )

    fun runG317(
        casesPerSeed: Int,
        seeds: List<Long>,
        progressCallback: ((Progress) -> Unit)? = null
    ): String {
        require(casesPerSeed >= 0) { "casesPerSeed must be non-negative" }
        require(seeds.isNotEmpty()) { "At least one seed is required" }

        val started = System.nanoTime()
        val totalCases = casesPerSeed * seeds.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val workerCount = minOf(4, seeds.size, availableProcessors)
        val perSeedProgress = AtomicIntegerArray(seeds.size)
        val executor = Executors.newFixedThreadPool(workerCount)

        val futures = seeds.mapIndexed { index, seed ->
            executor.submit<SvgPathDataOptimizer.G317FinalValidationClassificationResult> {
                SvgPathDataOptimizer.runG317FinalValidationClassificationStressSearch(
                    caseCount = casesPerSeed,
                    seed = seed,
                    maximumWitnesses = 32,
                    progressCallback = { currentSeedProcessed ->
                        perSeedProgress.set(index, currentSeedProcessed)
                        if (progressCallback != null) {
                            val snapshot = List(seeds.size) { seedIndex ->
                                perSeedProgress.get(seedIndex)
                            }
                            progressCallback.invoke(
                                Progress(
                                    completedCases = snapshot.sum(),
                                    totalCases = totalCases,
                                    workerCount = workerCount,
                                    perSeedProcessed = snapshot,
                                    casesPerSeed = casesPerSeed
                                )
                            )
                        }
                    }
                )
            }
        }

        val results = try {
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val valid = results.sumOf { it.validCases }
        val rejected = results.sumOf { it.rejectedGeneratedCases }
        val changed = results.sumOf { it.candidateChangedCases }
        val unchanged = results.sumOf { it.candidateUnchangedCases }
        val failures = results.sumOf { it.reproducedFinalValidationFailures }
        val failuresChanged = results.sumOf { it.failureWithChangedCandidate }
        val failuresUnchanged = results.sumOf { it.failureWithUnchangedCandidate }
        val sourceInvalid = results.sumOf { it.sourceAlreadyInvalid }
        val pass1Introduced = results.sumOf { it.firstPassIntroducedInvalidity }
        val candidateIntroduced = results.sumOf { it.candidateIntroducedInvalidity }
        val candidatePreserved = results.sumOf { it.candidatePreservedExistingInvalidity }
        val pass2Recovered = results.sumOf { it.pass2RecoveredValidity }
        val pass2Invalid = results.sumOf { it.pass2StillInvalid }
        val pass3Invalid = results.sumOf { it.pass3StillInvalid }
        val pass2Mismatches = results.sumOf { it.failureCandidatePass2Mismatches }
        val nonFixed = results.sumOf { it.failureNonFixedSecondPasses }
        val historicalExpected = results.mapNotNull { it.expectedHistoricalFailures }
        val historicalChecksRequired = historicalExpected.size
        val historicalChecksPassed = results.count {
            it.expectedHistoricalFailures != null && it.historicalFailureCoverageMatched
        }
        val historicalMatched = historicalChecksRequired == 0 || historicalChecksPassed == historicalChecksRequired
        val reasonCounts = linkedMapOf<String, Int>()
        results.forEach { result ->
            result.validatorReasonCounts.forEach { (reason, count) ->
                reasonCounts[reason] = (reasonCounts[reason] ?: 0) + count
            }
        }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.17 automated final-validation failure classification")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("G3.15 candidate changed: $changed")
            appendLine("G3.15 candidate unchanged: $unchanged")
            appendLine("Reproduced G3.16 final-validation failures: $failures")
            if (historicalChecksRequired > 0) {
                appendLine("Historical G3.16 expected validation failures: ${historicalExpected.sum()}")
                appendLine("Historical coverage checks passed: $historicalChecksPassed / $historicalChecksRequired")
                appendLine("Historical validation-failure coverage reproduced: $historicalMatched")
            }
            appendLine("Failures with changed candidate: $failuresChanged")
            appendLine("Failures with unchanged candidate: $failuresUnchanged")
            appendLine("Source already invalid: $sourceInvalid")
            appendLine("Pass 1 introduced invalidity: $pass1Introduced")
            appendLine("Candidate introduced invalidity: $candidateIntroduced")
            appendLine("Candidate preserved existing invalidity: $candidatePreserved")
            appendLine("Pass 2 recovered validity: $pass2Recovered")
            appendLine("Pass 2 still invalid: $pass2Invalid")
            appendLine("Pass 3 still invalid: $pass3Invalid")
            appendLine("Failure candidate/pass-2 mismatches: $pass2Mismatches")
            appendLine("Failure non-fixed second passes: $nonFixed")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            if (reasonCounts.isNotEmpty()) {
                appendLine()
                appendLine("Validator failure reasons")
                reasonCounts.toSortedMap().forEach { (reason, count) ->
                    appendLine("• $reason: $count")
                }
            }
            appendLine()
            when {
                !historicalMatched -> {
                    appendLine("RESULT: INVALID TEST — G3.17 did not reproduce the historical 14-case G3.16 validation signal.")
                    appendLine("Recommendation: repair the diagnostic harness before making a production-enablement decision.")
                }
                failuresChanged > 0 || candidateIntroduced > 0 -> {
                    appendLine("RESULT: G3.17 found validation failures that could implicate a changed G3.15 convergence candidate.")
                    appendLine("Recommendation: keep G3.15 shadow-only and inspect every failure witness.")
                }
                failures == 14 && failuresUnchanged == 14 -> {
                    appendLine("RESULT: G3.17 reproduced all 14 historical G3.16 final-validation failures, and every failure occurred where the G3.15 candidate was unchanged.")
                    if (pass1Introduced > 0) {
                        appendLine("Recommendation: the convergence guard is not implicated; classify the pass-1 validator failures separately before production enablement.")
                    } else {
                        appendLine("Recommendation: the convergence guard is not implicated; treat the signal as baseline corpus invalidity and proceed to the guarded production-enablement step after the locked suite remains clean.")
                    }
                }
                valid > 0 -> {
                    appendLine("RESULT: G3.17 completed without finding a changed-candidate validation failure.")
                    appendLine("Recommendation: review the witness classifications before production enablement.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }

            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("────────────────────────────────")
                appendLine("Seed ${index + 1}")
                append(result.toPlainTextReport())
            }
        }
    }

}
