package ir.lisa

import java.io.BufferedWriter
import java.io.FileWriter

fun main(args: Array<String>) {

    val lisa = Engine()
    
    //cosine
    if (true) {
        var output = ""
        var sumAp = 0
        var count = 0
        output += "Hit: ${lisa.hit}\n"

        for (query in lisa.queryList) {
            val cosine_scores = lisa.cosine(query)
            sumAp += lisa.getAP(query, cosine_scores, lisa.hit).toInt()
            count++
            val result = lisa.formatMeasurementOfScoresResult(query, cosine_scores)
            output += result
            println(result)
        }
        val map: Double = if (count == 0) 0.0 else sumAp / count.toDouble()
        val txt = String.format("MAP: %1.5f", map)
        output += "\n" + txt
        println(txt)
        writeToFile("cosine.txt", output)
    }

    //likelihood
    if (true) {
        var output = ""
        var sumAp = 0
        var count = 0
        output += "Hit: ${lisa.hit}\n"

        for (query in lisa.queryList) {
            val likelihood_scores = lisa.likelihood(query)
            sumAp += lisa.getAP(query, likelihood_scores, lisa.hit).toInt()
            count++
            val result = lisa.formatMeasurementOfScoresResult(query, likelihood_scores)
            output += result
            println(result)
        }
        val map: Double = if (count == 0) 0.0 else sumAp / count.toDouble()
        val txt = String.format("MAP: %1.5f", map)
        output += "\n" + txt
        println(txt)
        writeToFile("likelihood.txt", output)
    }

    //bm25
    if (true) {
        var output = ""
        var sumAp = 0
        var count = 0
        output += "Hit: ${lisa.hit}\n"

        for (query in lisa.queryList) {
            val bm25_scores = lisa.bm25(query)
            sumAp += lisa.getAP(query, bm25_scores, lisa.hit).toInt()
            count++
            val result = lisa.formatMeasurementOfScoresResult(query, bm25_scores)
            output += result
            println(result)
        }
        val map: Double = if (count == 0) 0.0 else sumAp / count.toDouble()
        val txt = String.format("MAP: %1.5f", map)
        output += "\n" + txt
        println(txt)
        writeToFile("bm25.txt", output)
    }
    
    lisa.close()
}

fun writeToFile(filename: String, txt: String, append: Boolean = false){
    val fileWriter = FileWriter(filename, append)
    val bufferedWriter = BufferedWriter(fileWriter)
    bufferedWriter.write(txt)
    bufferedWriter.close()
}