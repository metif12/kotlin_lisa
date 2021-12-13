package ir.lisa

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import java.io.IOException
import java.util.*


class Query(var text: String, val exID: String, relevantDocExIDs: Array<String>) {
    var tf: HashMap<String, Double>
    var relevant: ArrayList<String>
    var terms: ArrayList<String>
    var length = 0.0

    init {
        this.tf = HashMap()
        this.terms = ArrayList()
        this.relevant = arrayListOf<String>(*relevantDocExIDs)
        this.text = text.replace("\r\n", " ")

        val tokens = analyze(text)

        for (token in tokens) {
            if (!terms.contains(token)) {
                terms.add(token)
                tf[token] = 1.0
            } else {
                tf.replace(token, tf[token]!! + 1.0)
            }
        }

        for(t in terms) length += tf[t]!!
    }

    @Throws(IOException::class)
    fun analyze(text: String): List<String> {
        val analyzer = MyAnalyzer()
        val result: MutableList<String> = ArrayList()
        val tokenStream: TokenStream = analyzer.tokenStream("content", text)
        val attr: CharTermAttribute = tokenStream.addAttribute(CharTermAttribute::class.java)
        tokenStream.reset()
        while (tokenStream.incrementToken()) {
            result.add(attr.toString())
        }
        return result
    }
}