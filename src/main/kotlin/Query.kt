package ir.lisa

import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.tartarus.snowball.ext.PorterStemmer
import java.io.IOException
import java.util.*


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

        val tokens = analyze(text)

//        val tokens = text
//            .toLowerCase()
//            .replace("\r\n", " ")
//            .replace(".", " ")
//            .replace(",", " ")
//            .replace("-", " ")
//            .replace("(", " ")
//            .replace(")", " ")

        for (token in tokens) {
            if (!terms.contains(token)) {
                terms.add(token)
                tf[token] = 1
            } else {
                tf.replace(token, tf[token]!! + 1)
            }
        }

//        for (token in tokens.split(" ".toRegex())) {
//            if (token != "" && !EnglishAnalyzer.ENGLISH_STOP_WORDS_SET.contains(token)) {
//                porterStemmer.current = token
//                porterStemmer.stem()
//                val current = porterStemmer.current
//
//                if (!terms.contains(current)) {
//                    terms.add(current)
//                    tf[current] = 1
//                } else {
//                    tf.replace(current, tf[current]!! + 1)
//                }
//            }
//        }
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