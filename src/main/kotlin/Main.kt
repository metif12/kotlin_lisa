package ir.lisa

import org.apache.commons.io.IOUtil
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.FieldType
import org.apache.lucene.document.StringField
import org.apache.lucene.index.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.math.log10
import kotlin.math.sqrt

class LISA {
    var directory: Directory
    var directoryReader: DirectoryReader
    var analyzer: Analyzer = MyAnalyzer()
    var indexPath: Path = Path.of("index")
    var queries: ArrayList<Query> = ArrayList<Query>()

    private val config: IndexWriterConfig
        get() {
            val config = IndexWriterConfig(analyzer)
            config.openMode = IndexWriterConfig.OpenMode.CREATE
            return config
        }

    @Throws(IOException::class)
    fun close() {
        directoryReader.close()
        directory.close()
    }

    @Throws(IOException::class)
    fun buildIndex() {
        val indexWriter = IndexWriter(directory, config)
        indexWriter.commit()
        parseCorpus(indexWriter)
        indexWriter.commit()
        indexWriter.close()
    }

    @Throws(IOException::class)
    private fun parseCorpus(indexWriter: IndexWriter) {
        val corpusNames = arrayOf(
            "LISA0.001",
            "LISA0.501",
            "LISA1.001",
            "LISA1.501",
            "LISA2.001",
            "LISA2.501",
            "LISA3.001",
            "LISA3.501",
            "LISA4.001",
            "LISA4.501",
            "LISA5.001",
            "LISA5.501",
            "LISA5.627",
            "LISA5.850"
        )
        val contentFieldType = FieldType()
        contentFieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
        contentFieldType.setTokenized(true)
        contentFieldType.setStored(true)
        contentFieldType.setStoreTermVectors(true)
        contentFieldType.freeze()
        for (corpus_name in corpusNames) {
            val inputStream = LISA::class.java.getResourceAsStream("\\..\\..\\$corpus_name")
            if (inputStream != null) {
                val text = IOUtil.toString(inputStream)
                val split = text.split("\\r\\n\\*{44}\\r\\n *".toRegex())
                for (rawDoc in split) {
                    if(rawDoc == "") continue

                    val d = rawDoc.replace("\\r\\n *?\\r\\n".toRegex(), "\r\n\r\n")
                    val endID = d.indexOf("\r")
                    val id = d.substring(0, endID).replace("Document", "").trim()
                    val content = d.substring(endID + 2)
                    val doc = Document()
                    doc.add(Field("id", id, StringField.TYPE_STORED))
                    doc.add(Field("content", content, contentFieldType))
                    indexWriter.addDocument(doc)
                }
            }
        }
    }

    @Throws(IOException::class)
    fun loadQueries() {
        val queryHashMap = HashMap<String, Query>()
        val queriesInputStream = LISA::class.java.getResourceAsStream("\\..\\..\\LISA.QUE")
        val queriesDocInputStream = LISA::class.java.getResourceAsStream("\\..\\..\\LISA.REL")
        if (queriesInputStream != null) {
            val queriesCorpus = IOUtil.toString(queriesInputStream)
            for (rawQ in queriesCorpus.split("#\\r\\n".toRegex())) {
                if(rawQ =="") continue

                val endId = rawQ.indexOf("\r\n")
                val idQuery = rawQ.substring(0, endId).trim()
                val textQuery = rawQ.substring(endId + 2)
                queryHashMap[idQuery] = Query(textQuery, idQuery, arrayOf<String>())
            }
        }
        if (queriesDocInputStream != null) {
            val queriesAnswersCorpus = IOUtil.toString(queriesDocInputStream)
            for (rawQAnswer in queriesAnswersCorpus.split(" -1\\r\\n\\r\\n".toRegex())) {
                if(rawQAnswer =="") continue

                val queryAnswers = rawQAnswer.replace("Query ", "")
                val endAnswersId = queryAnswers.indexOf("\r\n", 0)
                val idAnswers = queryAnswers.substring(0, endAnswersId).trim()
                val startAnswers = queryAnswers.lastIndexOf("\r\n") + 2
                val textAnswers = queryAnswers.substring(startAnswers).replace(" -1", "")
                val query = queryHashMap[idAnswers]
                if (query != null) {
                    queries.add(Query(query.text, query.exID, textAnswers.split(" ".toRegex()).toTypedArray()))
                }
            }
        }
    }

    @Throws(IOException::class)
    fun cosine(query: Query?): ArrayList<Score> {
        val scores = ArrayList<Score>()
        val N = directoryReader.maxDoc()
        for (docId in 0 until directoryReader.maxDoc()) {
            var sumUp = 0.0
            var sumDownQue = 0.0
            var sumDownDoc = 0.0
            val doc = directoryReader.document(docId)
            val externalDocId = doc["id"]
            val vector = directoryReader.getTermVector(docId, "content") ?: continue
            val terms = vector.iterator()
            var bytesRef: BytesRef?

            while (true) {

                bytesRef = terms.next()

                if(bytesRef == null) break

                val docTerm = bytesRef.utf8ToString()

                //if (!query.terms.contains(doc_term)) continue;
                val docTf = terms.totalTermFreq()

                //if (doc_tf <= 0) continue;
                val queTf = query!!.tf[docTerm]
                val docDf = directoryReader.docFreq(Term("content", docTerm))
                val docIdf = log10((N.toFloat() / docDf).toDouble())
                val docWeight = log10((docTf + 1).toDouble()) * docIdf
                val qWeight = log10(((queTf ?: 0) + 1).toDouble()) * docIdf
                sumUp += qWeight * docWeight
                sumDownQue += qWeight * qWeight
                sumDownDoc += docWeight * docWeight
            }
            val score = sumUp / (Math.sqrt(sumDownDoc) * Math.sqrt(sumDownQue))
            if (score > 0) scores.add(Score(score, externalDocId))
        }
        scores.sortWith(Comparator { o1: Score, o2: Score ->
            o1.score.compareTo(o2.score) * -1
        })
        return scores
    }

    @Throws(IOException::class)
    fun bm25(query: Query?): ArrayList<Score> {
        val scores = ArrayList<Score>()
        val N = directoryReader.maxDoc()
        var avgDl = 0

        //for calc avgFieldLength
        for (docId in 0 until directoryReader.maxDoc()) {
            avgDl += directoryReader.getTermVector(docId, "content").sumTotalTermFreq.toInt()
        }
        avgDl /= N
        for (docId in 0 until directoryReader.maxDoc()) {
            var score = 0.0
            val doc = directoryReader.document(docId)
            val externalDocId = doc["id"]
            val vector = directoryReader.getTermVector(docId, "content") ?: continue
            val dl = vector.sumTotalTermFreq
            val terms = vector.iterator()
            val k1 = 2.0f
            val b = 0.75f
            var bytesRef: BytesRef?

            while (true) {

                bytesRef = terms.next()

                if(bytesRef == null) break

                val doc_term = bytesRef.utf8ToString()
                if (!query!!.terms.contains(doc_term)) continue
                val tf = terms.totalTermFreq()

                //if (tf <= 0) continue;
                val df = directoryReader.docFreq(Term("content", doc_term))
                val idf = Math.log(((N - df + 0.5f) / (df + 0.5f) + 1).toDouble())
                score += idf * tf * (k1 + 1) / (tf + k1 * (1 - b + b * (dl / avgDl.toDouble())))
            }
            if (score > 0) scores.add(Score(score, externalDocId))
        }
        scores.sortWith(Comparator { o1: Score, o2: Score ->
            o1.score.compareTo(o2.score) * -1
        })
        return scores
    }

    @Throws(IOException::class)
    fun likelihood(query: Query?): ArrayList<Score> {
        val scores = ArrayList<Score>()
        for (docId in 0 until directoryReader.maxDoc()) {
            var score = 1.0
            val doc = directoryReader.document(docId)
            val externalDocId = doc["id"]
            val vector = directoryReader.getTermVector(docId, "content") ?: continue
            val dl = vector.sumTotalTermFreq
            val terms = vector.iterator()
            var bytesRef: BytesRef?

            while (true) {

                bytesRef = terms.next()

                if(bytesRef == null) break
                val doc_term = bytesRef.utf8ToString()

                //if (!query.terms.contains(doc_term)) continue;
                val doc_tf = terms.totalTermFreq()

                //if (doc_tf <= 0) continue;
                val que_tf = query!!.tf[doc_term]
                val prob = Math.pow(doc_tf.toDouble() / dl, que_tf?.toDouble() ?: 1.toDouble())
                score *= if (prob == 0.0) 1e-15 else prob
            }
            if (score > 0) scores.add(Score(score, externalDocId))
        }
        scores.sortWith(Comparator { o1: Score, o2: Score ->
            o1.score.compareTo(o2.score) * -1
        })
        return scores
    }

    var hit = 20
    fun formatMeasurementOfScoresResult(query: Query?, scores: ArrayList<Score>): String {
        val t = StringBuilder()
        t.append('\n')
        t.append(String.format("Query: %s", query!!.text.replace("\r\n", " ")))
        t.append('\n')
        t.append("relevant: ")
        run {
            var i = 0
            while (i < hit && i < query.relevantIDs.size) {
                val exId = query.relevantIDs[i]
                t.append(String.format("%4d, ", Integer.valueOf(exId)))
                i++
            }
        }
        t.append('\n')
        t.append(String.format("TOP@%d: ", hit))
        var i = 0
        while (i < hit && i < scores.size) {
            val exId = scores[i].exID
            val score = scores[i].score
            t.append(String.format("%4d(%1.5f), ", Integer.valueOf(exId), score))
            i++
        }
        val recall = getRecall(query, scores, hit)
        val precision = getPrecision(query, scores, hit)
        val fScore = getFScore(query, scores, hit)
        val ap = getAP(query, scores, hit)
        val ndcg = getNDCG(query, scores, hit)
        t.append('\n')
        t.append(
            String.format(
                "recall=%1.5f    precision=%1.5f    f-score=%1.5f    avg-p=%1.5f    n-dcg=%1.5f",
                recall,
                precision,
                fScore,
                ap,
                ndcg
            )
        )
        t.append('\n')
        t.append("---------------------------------------------------------------------------------")
        return t.toString()
    }

    private fun getNDCG(query: Query?, scores: ArrayList<Score>, k: Int): Double {
        var dcg = 0
        var len = 0
        var i = 0
        while (i < k && i < scores.size) {
            if (isRelevant(query, scores[i].exID)) {
                val log: Double = if (i == 1) 1.0 else Math.log10(i.toDouble()) / log10(2.0)
                val cg = scores[i].score / log
                dcg += cg.toInt()
                len += (cg * cg).toInt()
            }
            i++
        }
        return if (dcg == 0) 0.0 else dcg / sqrt(len.toDouble())
    }

    fun getAP(query: Query?, scores: ArrayList<Score>, k: Int): Double {
        var n = 0
        var sum = 0
        var i = 0
        while (i < k && i < scores.size) {
            if (isRelevant(query, scores[i].exID)) {
                n++
                sum += (n.toDouble() / i).toInt()
            }
            i++
        }
        return if (n == 0) 0.0 else sum / n.toDouble()
    }

    private fun getFScore(query: Query?, scores: ArrayList<Score>, k: Int): Double {
        val recall = getRecall(query, scores, k)
        val precision = getPrecision(query, scores, k)
        return 1.0 / (0.5.toFloat() * (1 / recall)) + (1 - 0.5.toFloat()) * (1 / precision)
    }

    private fun getRecall(query: Query?, scores: ArrayList<Score>, k: Int): Double {
//        return (double) getTP(query,scores,k) / (getTP(query,scores,k) + getFN(query,scores,k));
        return getTP(query, scores, k).toDouble() / query!!.relevantIDs.size
    }

    private fun getPrecision(query: Query?, scores: ArrayList<Score>, k: Int): Double {
//        return (double) getTP(query,scores,k) / (getTP(query,scores,k) + getFP(query,scores,k));
        return getTP(query, scores, k).toDouble() / k
    }

    private fun getTN(query: Query, scores: ArrayList<Score>, k: Int): Int {
        return scores.size - getFP(query, scores, k)
    }

    private fun getFN(query: Query, scores: ArrayList<Score>, k: Int): Int {
        return query.relevantIDs.size - getTP(query, scores, k)
    }

    private fun getFP(query: Query, scores: ArrayList<Score>, k: Int): Int {
        return k - getTP(query, scores, k)
    }

    private fun getTP(query: Query?, scores: ArrayList<Score>, k: Int): Int {
        var tp = 0
        var i = 0
        while (i < k && i < scores.size) {
            if (isRelevant(query, scores[i].exID)) {
                tp++
            }
            i++
        }
        return tp
    }

    private fun isRelevant(query: Query?, exID: String): Boolean {
        return query!!.relevantIDs.contains(exID)
    }

    init {
        if (!Files.exists(indexPath)) Files.createDirectory(indexPath)
        directory = FSDirectory.open(indexPath)
        buildIndex()
        directoryReader = DirectoryReader.open(directory)
    }
}

fun main(args: Array<String>) {
    println("IR PROJECT: LISA Corpus")
    val lisa = LISA()
    lisa.buildIndex()
    lisa.loadQueries()
    lisa.hit = 100
    val enable_cosine = true
    val enable_likelihood = true
    val enable_bm25 = true
    if (enable_cosine) {
        val writer = BufferedWriter(FileWriter("cosine_res.txt"))
        var sumAp = 0
        var count = 0
        writer.write(
            """
                        Hit: ${lisa.hit}
                        
                        """.trimIndent()
        )
        for (query in lisa.queries) {
            val cosine_scores = lisa.cosine(query)
            sumAp += lisa.getAP(query, cosine_scores, lisa.hit).toInt()
            count++
            val result = lisa.formatMeasurementOfScoresResult(query, cosine_scores)
            writer.write(result)
            println(result)
        }
        val map: Double = if (count == 0) 0.0 else sumAp / count.toDouble()
        val txt = String.format("MAP: %1.5f", map)
        writer.write("\n")
        writer.write(txt)
        println(txt)
        writer.close()
    }
    if (enable_likelihood) {
        val writer = BufferedWriter(FileWriter("likelihood_res.txt"))
        var sumAp = 0
        var count = 0
        writer.write(
            """
                        Hit: ${lisa.hit}
                        
                        """.trimIndent()
        )
        for (query in lisa.queries) {
            val likelihood_scores = lisa.likelihood(query)
            sumAp += lisa.getAP(query, likelihood_scores, lisa.hit).toInt()
            count++
            val result = lisa.formatMeasurementOfScoresResult(query, likelihood_scores)
            writer.write(result)
            println(result)
        }
        val map: Double = if (count == 0) 0.0 else sumAp / count.toDouble()
        val txt = String.format("MAP: %1.5f", map)
        writer.write("\n")
        writer.write(txt)
        println(txt)
        writer.close()
    }
    if (enable_bm25) {
        val writer = BufferedWriter(FileWriter("bm25_res.txt"))
        var sumAp = 0
        var count = 0
        writer.write(
            """
                        Hit: ${lisa.hit}
                        
                        """.trimIndent()
        )
        for (query in lisa.queries) {
            val bm25_scores = lisa.bm25(query)
            sumAp += lisa.getAP(query, bm25_scores, lisa.hit).toInt()
            count++
            val result = lisa.formatMeasurementOfScoresResult(query, bm25_scores)
            writer.write(result)
            println(result)
        }
        val map: Double = if (count == 0) 0.0 else sumAp / count.toDouble()
        val txt = String.format("MAP: %1.5f", map)
        writer.write("\n")
        writer.write(txt)
        println(txt)
        writer.close()
    }
    lisa.close()
}