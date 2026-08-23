package com.littlemachineworks.svgvectorconverter

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/** Android-free parallel entry point for the G3.10 bidirectional polyline comparator. */
object SvgBidirectionalPolylineGeometrySearch {
    data class Progress(
        val completedCases:Int,val totalCases:Int,val workerCount:Int,
        val perSeedProcessed:List<Int>,val casesPerSeed:Int
    ){
        val percentComplete:Double get()=if(totalCases<=0)100.0 else completedCases*100.0/totalCases
    }

    internal data class ResumeState(
        val casesPerSeed:Int,
        val seeds:List<Long>,
        val perSeedStates:List<SvgPathDataOptimizer.BidirectionalPolylinePartialState>
    )

    internal fun runDefault(
        progressCallback:((Progress)->Unit)?=null,
        controlCheckpoint:(()->Unit)?=null,
        resumeState:ResumeState?=null,
        checkpointCallback:((ResumeState)->Unit)?=null
    ):String=run(
        25_000,
        listOf(0x6316_2026L,0x6316_0001L,0x6316_0002L,0x1D40_2026L),
        progressCallback,
        controlCheckpoint,
        resumeState,
        checkpointCallback
    )

    internal fun run(
        casesPerSeed:Int,
        seeds:List<Long>,
        progressCallback:((Progress)->Unit)?=null,
        controlCheckpoint:(()->Unit)?=null,
        resumeState:ResumeState?=null,
        checkpointCallback:((ResumeState)->Unit)?=null
    ):String{
        require(casesPerSeed>=0);require(seeds.isNotEmpty())
        val validResume = resumeState?.takeIf {
            it.casesPerSeed==casesPerSeed && it.seeds==seeds && it.perSeedStates.size==seeds.size
        }
        val initialStates = validResume?.perSeedStates ?: List(seeds.size){
            SvgPathDataOptimizer.BidirectionalPolylinePartialState()
        }
        val started=System.nanoTime();val total=casesPerSeed*seeds.size
        val processors=Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val workers=minOf(4,seeds.size,processors)
        val progress=AtomicIntegerArray(seeds.size)
        initialStates.forEachIndexed { i,state -> progress.set(i,state.processedCases) }
        val stateLock=Any()
        val latestStates=initialStates.toMutableList()
        fun publishProgress(){
            val snapshot=List(seeds.size){progress.get(it)}
            progressCallback?.invoke(Progress(snapshot.sum(),total,workers,snapshot,casesPerSeed))
        }
        fun publishCheckpoint(index:Int,state:SvgPathDataOptimizer.BidirectionalPolylinePartialState){
            synchronized(stateLock){
                latestStates[index]=state
                checkpointCallback?.invoke(ResumeState(casesPerSeed,seeds,latestStates.toList()))
            }
        }
        publishProgress()
        val executor=Executors.newFixedThreadPool(workers)
        val futures=seeds.mapIndexed{index,seed->executor.submit<SvgPathDataOptimizer.BidirectionalPolylineResult>{
            SvgPathDataOptimizer.runBidirectionalPolylineGeometryStressSearch(
                casesPerSeed,
                seed,
                64,
                { processed ->
                    progress.set(index,processed)
                    publishProgress()
                },
                controlCheckpoint,
                initialStates[index],
                { state -> publishCheckpoint(index,state) }
            )
        }}
        val results=try{futures.map{it.get()}}finally{executor.shutdownNow()}
        val valid=results.sumOf{it.validCases};val rejected=results.sumOf{it.rejectedGeneratedCases}
        val changed=results.sumOf{it.candidateChangedCases};val col=results.sumOf{it.collinearChangedCases}
        val cand=results.sumOf{it.candidateMismatchCases};val direct=results.sumOf{it.directCollinearMismatchCases}
        val sf=results.sumOf{it.sourceToFirstMismatchCases};val ss=results.sumOf{it.sourceToSecondMismatchCases}
        val checks=results.sumOf{it.comparisons};val elapsed=System.nanoTime()-started
        return buildString{
            appendLine("G3.10 automated bidirectional polyline geometry comparator differential stress search")
            appendLine();appendLine("Seeds: ${seeds.size}");appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workers");appendLine("Available processors: $processors")
            appendLine("Valid comparisons: $valid");appendLine("Rejected generated cases: $rejected")
            appendLine("G3.7 candidate changed: $changed");appendLine("Collinear-consolidation stage changed: $col")
            appendLine("Bidirectional candidate mismatches: $cand")
            appendLine("Bidirectional direct-collinear mismatches: $direct")
            appendLine("Source → pass-1 bidirectional mismatches: $sf")
            appendLine("Source → pass-2 bidirectional mismatches: $ss")
            appendLine("Bidirectional comparisons: $checks")
            appendLine("Elapsed this process: "+String.format(java.util.Locale.US,"%.2f ms",elapsed/1_000_000.0))
            appendLine("Accumulated search CPU time: "+String.format(java.util.Locale.US,"%.2f ms",results.sumOf{it.elapsedNanos}/1_000_000.0))
            appendLine()
            if(direct>0){
                appendLine("RESULT: G3.10 found direct collinear geometry differences under bidirectional polyline distance.")
                appendLine("Recommendation: keep production unchanged and inspect every direct-collinear witness.")
            }else if(cand>0||ss>0){
                appendLine("RESULT: G3.10 cleared the direct collinear signal but found residual geometry differences elsewhere.")
                appendLine("Recommendation: keep production unchanged and investigate the residual witnesses.")
            }else{
                appendLine("RESULT: G3.10 classified the G3.9 direct-collinear signal as a comparator artifact on this corpus.")
                appendLine("Recommendation: adopt the bidirectional comparator for diagnostics, then rerun G3.7 before any production change.")
            }
            results.forEachIndexed{i,r->appendLine();appendLine("────────────────────────────────");appendLine("Seed ${i+1}");append(r.toPlainTextReport())}
        }
    }
}
