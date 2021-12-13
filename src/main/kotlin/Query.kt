package ir.lisa

import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.tartarus.snowball.ext.PorterStemmer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Query(var text: String, val exID: String, relevantDocExIDs: Array<String>) {
    var tf: HashMap<String, Int>
    var relevant: ArrayList<String>
    var terms: ArrayList<String>

    init {
        this.tf = HashMap()
        this.terms = ArrayList()
        this.relevant = arrayListOf<String>(*relevantDocExIDs)
        this.text = text.replace("\r\n", " ")
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
                porterStemmer.stem()
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