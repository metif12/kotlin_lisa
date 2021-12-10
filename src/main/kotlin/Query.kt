package ir.lisa

import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.tartarus.snowball.ext.PorterStemmer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Query(val text: String, val exID: String, relevantDocExIDs: Array<String>) {
    var tf: HashMap<String, Int>
    var relevantIDs: ArrayList<String>
    var terms: ArrayList<String>

    init {
        tf = HashMap()
        terms = ArrayList()
        this.relevantIDs = arrayListOf<String>(*relevantDocExIDs)
        val porterStemmer = PorterStemmer()
        val tokens = text
            .lowercase(Locale.getDefault())
            .replace("\r\n", " ")
            .replace(".", " ")
            .replace(",", " ")
            .replace("-", " ")
            .replace("(", " ")
            .replace(")", " ")
            .split(" ".toRegex()).toTypedArray()

        for (token in tokens) {
            if (token != "" && !EnglishAnalyzer.ENGLISH_STOP_WORDS_SET.contains(token)) {
                porterStemmer.current = token
                val current = porterStemmer.current
                if (!terms.contains(current)) {
                    terms.add(current)
                    tf[current] = 1
                } else {
                    tf.replace(current, tf[current]!! + 1)
                }
            }
        }
    }
}