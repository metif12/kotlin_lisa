package ir.lisa

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.LowerCaseFilter
import org.apache.lucene.analysis.core.StopFilter
import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.en.PorterStemFilter
import org.apache.lucene.analysis.standard.StandardTokenizer

class MyAnalyzer : Analyzer() {
    override fun createComponents(txt: String): TokenStreamComponents {
        //tokenize
        val standardTs = TokenStreamComponents(StandardTokenizer())
        //lowercase
        val lowercaseTs = TokenStreamComponents(standardTs.source, LowerCaseFilter(standardTs.tokenStream))
        //stop words
        val stopFilter = StopFilter(lowercaseTs.tokenStream, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET)
        val stopWordTs = TokenStreamComponents(lowercaseTs.source, stopFilter)
        //stemming
        return TokenStreamComponents(stopWordTs.source, PorterStemFilter(stopWordTs.tokenStream))
    }
}