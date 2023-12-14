
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.math.max

class Searcher {
    var badCharacterTable: Map<Char, Int> = emptyMap<Char, Int>()
    fun recursiveFileSearch(path: String, pattern: CharArray, noHeading: Boolean = false,
                            hidden: Boolean = false, linesBefore: Int? = null,
                            linesAfter: Int? = null, contextLines: Int? = null){
        val paths: List<Path> = Path(path).listDirectoryEntries()
        for (p in paths) {
            //skip hidden files, unless the option is set
            if (Path(p.toString()).isHidden() && !hidden){
                continue
            }
            if (Path(p.toString()).isRegularFile()) {
                //thread(start = true) {
                searchAllLines(p.toString(), pattern, noHeading, linesBefore, linesAfter, contextLines)
                //}
            }
            else if (Path(p.toString()).isDirectory()) {
                recursiveFileSearch(p.toString(), pattern)
            }
        }
    }
    fun searchAllLines(filePath: String, pattern: CharArray, noHeading: Boolean,
                       linesBefore: Int?, linesAfter: Int?, contextLines: Int?){

        badCharacterTable = createBadCharacterShiftTable(pattern)
        val inputStream: InputStream = File(filePath).inputStream()
        var resultList: MutableList<String> = mutableListOf()
    //iterates through lines and calls searchStringInText (Boyer Moore Horspool algorithm)
    //depending on subcommands, the required lines will be aggregated by the respective
    // aggregatePrintLines... function
        when
        {
            (linesBefore == null && linesAfter == null && contextLines == null)
            -> resultList = aggregatePrintLinesNoContext(inputStream, filePath, pattern)
            (linesBefore != null)
            -> resultList = aggregatePrintLinesBeforeMatch(inputStream, filePath, pattern, linesBefore)
            (linesAfter != null)
            -> resultList = aggregatePrintLinesAfterMatch(inputStream, filePath, pattern, linesAfter)
            (contextLines != null)
            -> resultList = aggregatePrintLinesWithContext(inputStream, filePath, pattern, contextLines)
        }

        printResult(resultList, noHeading)
    }

    private fun aggregatePrintLinesBeforeMatch(
        inputStream: InputStream, filePath: String,
        pattern: CharArray, linesBefore: Int): MutableList<String>  {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultList: MutableList<String> = mutableListOf()
        var offset = linesBefore

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val isMatch = searchStringInText(pattern, line)

            if (!isMatch) {
                if (partialResultList.count() > offset)
                    partialResultList.removeFirst()
                partialResultList.add(filePath + "-" + lineCount + "-" + it.toString() + "\n")
            }
            else {
                if (partialResultList.count() > offset)
                    partialResultList.removeFirst()
                partialResultList.add(filePath + ":" + lineCount + ":" + it.toString() + "\n")
                resultList.addAll(partialResultList)
                partialResultList.clear()
            }
            lineCount++

        }
        return resultList
    }

    private fun aggregatePrintLinesAfterMatch(
        inputStream: InputStream, filePath: String,
        pattern: CharArray, linesBefore: Int): MutableList<String>  {
        return mutableListOf()
    }

    private fun aggregatePrintLinesWithContext(
        inputStream: InputStream,filePath: String,
        pattern: CharArray, linesBefore: Int): MutableList<String>  {
        return mutableListOf()
    }

    fun aggregatePrintLinesNoContext(inputStream: InputStream, filePath: String, pattern:CharArray): MutableList<String> {
        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val isMatch = searchStringInText(pattern, line)

            if (isMatch) {
                resultList.add(filePath + ":" + lineCount + ":" + it.toString() + "\n")
            }
            lineCount++
        }
        return resultList
    }

    /**This method creates the look-up table delta 1 (See paper).
     * It contains the shift values for all characters of a search pattern.
     * If we encounter a mismatch, we take the mismatching character from the text T
     * (the text that we compare our pattern against) and look it up in the badCharacterShiftTable.
     * The shift value for that specific character will tell us how far we can skip our pattern ahead.
     * The shift value for characters that are not in the table is always the length of the pattern.
     * */
    fun createBadCharacterShiftTable(pattern: CharArray): MutableMap<Char, Int>
    {
        val badCharacterTable = mutableMapOf<Char, Int>()
        for (i in pattern.indices){
            if (!badCharacterTable.containsKey(pattern[i])){
                badCharacterTable.put(pattern[i], max(1,pattern.size - i - 1))
            }
            else //overwrite shift value of char with the shift value of the char that occurs at a higher index
            {
                badCharacterTable[pattern[i]] = max(1, pattern.size - i - 1)
            }
        }
        return badCharacterTable
    }
    fun printResult(result: MutableList<String>, noHeading: Boolean){
        for (i in result.indices)
            print(result[i])
        //separates printed lines of different files
        if (noHeading)
            println("--")
    }
    //preprocess line as well as pattern into a sequence of chars
    fun searchStringInText(pattern: CharArray, line: CharArray): Boolean
    {
        val patternLen = pattern.size
        val lineLen = line.size
        var patIndex = patternLen - 1 //we compare the pattern with the line from left to right, but start from the right side of the pattern to look for matches in the line
        var lineIndex = patIndex
        var shiftValue = 0
        var shiftTotal = 0
        var matchedChar = 0

        //lineIndex < lineLen
        while(lineLen - shiftTotal >= patternLen )
        {
            if(pattern[patIndex].equals(line[lineIndex]))
            {
                if (patIndex == 0)
                    return true
                ++matchedChar
                --patIndex
                --lineIndex
            }
            else
            {
                lineIndex += matchedChar
                shiftValue = getShiftValue(line, lineIndex, patternLen)
                lineIndex += shiftValue
                shiftTotal += shiftValue
                //reset index to start from the right again
                patIndex = patternLen -1
                matchedChar = 0
            }
        }
        return false
    }

    fun getShiftValue(line: CharArray, lineIndex: Int, patternLen: Int): Int
    {
        return badCharacterTable[line[lineIndex]] ?: patternLen
    }

    fun preprocess(line: String, ignoreCase: Boolean = false): CharArray {
        var result = ""
        if (ignoreCase)
            result = line.lowercase(Locale.getDefault());
        else
            result = line
        return result.toCharArray()
    }

}