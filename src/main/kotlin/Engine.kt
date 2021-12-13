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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt


class Engine {
    var directory: Directory
    var directoryReader: DirectoryReader
    var analyzer: Analyzer = MyAnalyzer()
    var indexPath: Path = Path.of("index")
    var queryList: ArrayList<Query> = ArrayList<Query>()

    private val config: IndexWriterConfig
        get() {
            val config = IndexWriterConfig(analyzer)
            config.openMode = IndexWriterConfig.OpenMode.CREATE
            return config
        }

    fun close() {
        directoryReader.close()
        directory.close()
    }

    private fun buildIndex() {
        val indexWriter = IndexWriter(directory, config)
        indexWriter.commit()
        parseCorpus(indexWriter)
        indexWriter.commit()
        indexWriter.close()
    }

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

        var lastId = 0
        for (corpus_name in corpusNames) {
            val inputStream = Engine::class.java.getResourceAsStream("\\..\\..\\$corpus_name")
            if (inputStream != null) {
                val text = IOUtil.toString(inputStream)
                val split = text.split("\\r\\n\\*{44}\\r\\n *".toRegex())
                for (rawDoc in split) {
                    if (rawDoc == "") continue

                    val d = rawDoc.replace("\\r\\n *?\\r\\n".toRegex(), "\r\n\r\n")
                    val endID = d.indexOf("\r")
                    val id = d.substring(0, endID).replace("Document", "").trim()
                    val content = d.substring(endID + 2)

                    if (id.toInt() != lastId + 1)
                        log("jump doc id from $lastId to $id\n")

                    val doc = Document()
                    doc.add(Field("id", id, StringField.TYPE_STORED))
                    doc.add(Field("content", content, contentFieldType))
                    indexWriter.addDocument(doc)

                    lastId = id.toInt()
                }
            }
        }
    }

    fun loadQueries() {
        val queryHashMap = HashMap<String, Query>()
        val queriesInputStream = Engine::class.java.getResourceAsStream("\\..\\..\\LISA.QUE")
        val queriesDocInputStream = Engine::class.java.getResourceAsStream("\\..\\..\\LISA.REL")

        var lastQId = 0

        if (queriesInputStream != null) {
            val queriesCorpus = IOUtil.toString(queriesInputStream)
            for (rawQ in queriesCorpus.split("#\\r\\n".toRegex())) {
                if (rawQ == "") continue

                val endId = rawQ.indexOf("\r\n")
                val idQuery = rawQ.substring(0, endId).trim()
                val textQuery = rawQ.substring(endId + 2)


                if (idQuery.toInt() != lastQId + 1)
                    log("jump query id from $lastQId to $idQuery\n")

                if (textQuery == "") continue

                queryHashMap[idQuery] = Query(textQuery, idQuery, arrayOf<String>())

                lastQId = idQuery.toInt()
            }
        }

        if (queriesDocInputStream != null) {
            val queriesAnswersCorpus = IOUtil.toString(queriesDocInputStream)
            var lastQRId = 0
            for (rawQAnswer in queriesAnswersCorpus.split(" -1\\r\\n\\r\\n".toRegex())) {
                if (rawQAnswer == "") continue

                val queryAnswers = rawQAnswer.replace("Query ", "")
                val endAnswersId = queryAnswers.indexOf("\r\n", 0)
                val idAnswers = queryAnswers.substring(0, endAnswersId).trim()
                val startAnswers = queryAnswers.lastIndexOf("\r\n") + 2
                val textAnswers = queryAnswers.substring(startAnswers).replace(" -1", "")
                val query = queryHashMap[idAnswers]
                if (query != null) {
                    queryList.add(Query(query.text, query.exID, textAnswers.split(" ".toRegex()).toTypedArray()))
                }


                if (idAnswers.toInt() != lastQRId + 1)
                    log("jump answer id from $lastQRId to $idAnswers\n")

                lastQRId = idAnswers.toInt()
            }
        }
    }

    private fun getDocumentExId(docId: Int): String {
        return directoryReader.document(docId)["id"]
    }

    private fun getAverageFieldLength(): Double {
        var sumDL = 0L
        for (docId in 0 until directoryReader.maxDoc()) {
            sumDL += directoryReader.getTermVector(docId, "content").sumTotalTermFreq
        }
        return sumDL.toDouble() / directoryReader.maxDoc()
    }
    private fun getCorpusLength(): Long {
        var sumDL = 0L
        for (docId in 0 until directoryReader.maxDoc()) {
            sumDL += directoryReader.getTermVector(docId, "content").sumTotalTermFreq
        }
        return sumDL.toLong()
    }

    private fun docFrequency(docTerm: String?) = directoryReader.docFreq(Term("content", docTerm))
    private fun totalTermFrequency(docTerm: String?) = directoryReader.totalTermFreq(Term("content", docTerm))

    fun cosine(query: Query): ArrayList<Score> {
        val scores = ArrayList<Score>()
        val N = directoryReader.maxDoc()
        for (docId in 0 until directoryReader.maxDoc()) {
            var sumUp = 0.0
            var sumDownQue = 0.0
            var sumDownDoc = 0.0
            val externalDocId = getDocumentExId(docId)
            val vector = directoryReader.getTermVector(docId, "content") ?: continue
            val terms = vector.iterator()
            var bytesRef: BytesRef?

            while (true) {

                bytesRef = terms.next()

                if (bytesRef == null) break

                val docTerm = bytesRef.utf8ToString()

                val docTf = terms.totalTermFreq()
                val queTf = if (query.tf[docTerm] != null) log10((query.tf[docTerm] ?: 0.0) + 1.0) else 0.0
                val docDf = docFrequency(docTerm)
                val docIdf = log10(N.toDouble() / docDf)
                val docWeight = log10(docTf.toDouble()+ 1)  * docIdf
                val qWeight = queTf * docIdf
                sumUp += qWeight * docWeight
                sumDownQue += qWeight * qWeight
                sumDownDoc += docWeight * docWeight
            }

            val score = sumUp / (sqrt(sumDownDoc) * sqrt(sumDownQue))

            if(score > 0) scores.add(Score(score, externalDocId))
        }
        scores.sortWith { o1: Score, o2: Score -> o1.score.compareTo(o2.score) * -1 }
        return scores
    }

    fun bm25(query: Query): ArrayList<Score> {
        val scores = ArrayList<Score>()
        val N = directoryReader.maxDoc()
        var avgDl = getAverageFieldLength()

        for (docId in 0 until directoryReader.maxDoc()) {
            var score = 0.0
            val externalDocId = getDocumentExId(docId)
            val vector = directoryReader.getTermVector(docId, "content") ?: continue
            val dl = vector.sumTotalTermFreq
            val terms = vector.iterator()
            val k1 = 2.0f
            val b = 0.75f
            var bytesRef: BytesRef?

            while (true) {

                bytesRef = terms.next()

                if (bytesRef == null) break

                val docTerm = bytesRef.utf8ToString()

                if (!query.terms.contains(docTerm)) continue

                val tf = terms.totalTermFreq()

                if (tf <= 0) continue

                val df = docFrequency(docTerm)
                val idf = log10(N.toDouble() / df)

                score += idf * (tf * (k1 + 1) / (tf + k1 * (1 - b + (b * (dl / avgDl)))))
            }
            if(score > 0) scores.add(Score(score, externalDocId))
        }
        scores.sortWith { o1: Score, o2: Score -> o1.score.compareTo(o2.score) * -1 }
        return scores
    }

    fun likelihood(query: Query): ArrayList<Score> {
        val scores = ArrayList<Score>()
        val smoothingFactor = 0.35
        val corpusLength = getCorpusLength()

        for (docId in 0 until directoryReader.maxDoc()) {
            var score = 1.0
            val externalDocId = getDocumentExId(docId)
            val vector = directoryReader.getTermVector(docId, "content") ?: continue
            val dl = vector.sumTotalTermFreq
            val terms = vector.iterator()
            var bytesRef: BytesRef?


            while (true) {

                bytesRef = terms.next()

                if (bytesRef == null) break
                val docTerm = bytesRef.utf8ToString()

                val docTf = terms.totalTermFreq()
                val cf = totalTermFrequency(docTerm)

                val queTf = query.tf[docTerm]
                val prob = (1-smoothingFactor)*(docTf.toDouble() / dl) + smoothingFactor * (cf/ corpusLength)
                score *= prob.pow(queTf ?: 0.0)
            }

            if(score > 0) scores.add(Score(score, externalDocId))
        }
        scores.sortWith { o1: Score, o2: Score -> o1.score.compareTo(o2.score) * +1 }
        return scores
    }

    var hit = 100

    fun formatMeasurementOfScoresResult(query: Query, scores: ArrayList<Score>): String {
        val t = StringBuilder()
        t.append('\n')
        t.append(String.format("Query: %s\n", query.text.replace("\r\n", " ")))
        t.append(String.format("Query tokens: %s\n", listOf(query.terms).joinToString(separator = ", ")))
        t.append(String.format("relevant: %s\n", listOf(query.relevant).joinToString(separator = ", ")))

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
        t.append(
            String.format(
                "\nrecall=%1.5f    precision=%1.5f    f-score=%1.5f    avg-p=%1.5f    n-dcg=%1.5f",
                recall,
                precision,
                fScore,
                ap,
                ndcg
            )
        )
        t.append("\n---------------------------------------------------------------------------------")
        return t.toString()
    }

    private fun getNDCG(query: Query?, scores: ArrayList<Score>, k: Int): Double {
        var dcg = 0
        var len = 0
        var i = 0
        while (i < k && i < scores.size) {
            if (isRelevant(query, scores[i].exID)) {
                val log: Double = if (i == 1) 1.0 else log10(i.toDouble()) / log10(2.0)
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
        return getTP(query, scores, k).toDouble() / query!!.relevant.size
    }

    private fun getPrecision(query: Query?, scores: ArrayList<Score>, k: Int): Double {
//        return (double) getTP(query,scores,k) / (getTP(query,scores,k) + getFP(query,scores,k));
        return getTP(query, scores, k).toDouble() / k
    }

    private fun getTN(query: Query, scores: ArrayList<Score>, k: Int): Int {
        return scores.size - getFP(query, scores, k)
    }

    private fun getFN(query: Query, scores: ArrayList<Score>, k: Int): Int {
        return query.relevant.size - getTP(query, scores, k)
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
        return query!!.relevant.contains(exID)
    }

    init {
        //make sure index folder exists
        if (!Files.exists(indexPath))
            Files.createDirectory(indexPath)

        directory = FSDirectory.open(indexPath)

        buildIndex()

        directoryReader = DirectoryReader.open(directory)

        loadQueries()
    }

    fun log(msg: String) {
        writeToFile("logs.txt", msg, true)
    }
}