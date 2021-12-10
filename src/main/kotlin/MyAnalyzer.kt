package ir.lisa

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.LowerCaseFilter
import org.apache.lucene.analysis.core.StopFilter
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.en.PorterStemFilter
import org.apache.lucene.analysis.standard.StandardTokenizer

class MyAnalyzer : Analyzer() {
    override fun createComponents(s: String): TokenStreamComponents {
        val standard_ts = TokenStreamComponents(StandardTokenizer())
        val lowercase_ts = TokenStreamComponents(standard_ts.source, LowerCaseFilter(standard_ts.tokenStream))
        val stopFilter = StopFilter(lowercase_ts.tokenStream, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET)
        val stopword_ts = TokenStreamComponents(lowercase_ts.source, stopFilter)
        return TokenStreamComponents(stopword_ts.source, PorterStemFilter(stopword_ts.tokenStream))
    }
}