
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.math.max

class Searcher {
    var badCharacterTable: Map<Char, Int> = emptyMap()
    val printHelper = PrintHelper()

    fun recursiveFileSearch(path: String, pattern: CharArray, userInput: UserInput, linesBefore: Int? = null,
                            linesAfter: Int? = null, contextLines: Int? = null){
        val paths: List<Path> = Path(path).listDirectoryEntries()
        for (p in paths) {
            //skip hidden files, unless the option is set
            if (!userInput.hidden && p.isHidden()){
                continue
            }
            if (p.isRegularFile()) {
                //skip binary files, unless the option is set
                if (!userInput.binary && isBinaryFile(p.toString())){
                    continue
                }
                //thread(start = true) {
                searchAllLines(p.toString(), pattern, userInput.noHeading, userInput.color, linesBefore, linesAfter, contextLines)
                //}
            }
            else if (p.isDirectory()) {
                recursiveFileSearch(p.toString(), pattern, userInput, linesBefore, linesAfter, contextLines)
            }
        }
    }
    private fun searchAllLines(filePath: String, pattern: CharArray, noHeading: Boolean,
                               color: Boolean, linesBefore: Int?, linesAfter: Int?, contextLines: Int?){

        //badCharacterTable = createBadCharacterShiftTable(pattern)
        val inputStream: InputStream = File(filePath).inputStream()
        //iterates through lines and calls searchStringInText (Boyer Moore Horspool algorithm)
        //depending on subcommands, the required lines will be aggregated by the respective
        // aggregatePrintLines... function
        val resultList: MutableList<String> = when {
            (linesBefore != null)
            -> aggregatePrintLinesBeforeMatch(inputStream, filePath, pattern, color, noHeading, linesBefore)

            (linesAfter != null)
            -> aggregatePrintLinesAfterMatch(inputStream, filePath, pattern, color, noHeading, linesAfter)

            (contextLines != null)
            -> aggregatePrintLinesWithContext(inputStream, filePath, pattern, color, noHeading, contextLines)

            else -> aggregatePrintLinesNoContext(inputStream, filePath, pattern, color, noHeading)
        }

        printHelper.printResult(resultList)
    }

    private fun aggregatePrintLinesBeforeMatch(
        inputStream: InputStream, filePath: String, pattern: CharArray, color: Boolean,
        noHeading: Boolean, linesBefore: Int): MutableList<String>  {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultList: MutableList<String> = mutableListOf()

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (!isMatch) {
                if (partialResultList.count() > linesBefore) {
                    partialResultList.removeFirst()
                }
                partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount, it, isMatch, color, noHeading))
            }
            else {
                if (partialResultList.count() > linesBefore) {
                    partialResultList.removeFirst()
                }
                partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                    printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second), isMatch, color, noHeading))
                resultList.addAll(partialResultList)
                partialResultList.clear()
            }
            lineCount++
        }
        return resultList
    }

    private fun aggregatePrintLinesAfterMatch(inputStream: InputStream, filePath: String,
        pattern: CharArray, color: Boolean, noHeading: Boolean, linesAfter: Int): MutableList<String>  {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultList: MutableList<String> = mutableListOf()
        var matchInPartialResultList = false

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (isMatch){
                partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                    printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                    isMatch, color, noHeading))
                matchInPartialResultList = true
            }
            else
            {
                if (matchInPartialResultList)
                    partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount, it, isMatch, color, noHeading))
            }
            lineCount++

            if (partialResultList.count() > linesAfter){
                resultList.addAll(partialResultList)
                partialResultList.clear()
                matchInPartialResultList = false
            }
        }
        return resultList
    }

    private fun aggregatePrintLinesWithContext(
        inputStream: InputStream,filePath: String, pattern: CharArray,
        color: Boolean, noHeading: Boolean, contextLines: Int): MutableList<String>  {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultListBefore: MutableList<String> = mutableListOf()
        val partialResultListAfter: MutableList<String> = mutableListOf()
        var listHasMatch = false

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (!listHasMatch)
            {
                if (!isMatch)
                {
                    if (partialResultListBefore.count() > contextLines)
                    {
                        partialResultListBefore.removeFirst()
                    }
                    partialResultListBefore.add(printHelper.formatAndStyleLine(filePath, lineCount, it, isMatch, color, noHeading))
                }
                else
                {
                    if (partialResultListBefore.count() > contextLines)
                    {
                        partialResultListBefore.removeFirst()
                    }
                    partialResultListBefore.add(printHelper.formatAndStyleLine(filePath, lineCount,
                        printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                        isMatch, color, noHeading))
                    listHasMatch = true
                }
            }
            else
            {
                if (isMatch)
                {
                    if (partialResultListAfter.count() >= contextLines)
                    {
                        resultList.addAll(partialResultListBefore)
                        resultList.addAll(partialResultListAfter)
                        partialResultListBefore.clear()
                        partialResultListAfter.clear()
                        listHasMatch = false
                    }
                    else
                    {
                        partialResultListAfter.add(
                            printHelper.formatAndStyleLine(filePath, lineCount,
                                printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                                isMatch, color, noHeading))
                    }
                }
                else
                {
                    if (partialResultListAfter.count() >= contextLines)
                    {
                        resultList.addAll(partialResultListBefore)
                        resultList.addAll(partialResultListAfter)
                        partialResultListBefore.clear()
                        partialResultListAfter.clear()
                        listHasMatch = false
                    }
                    else
                    {
                        partialResultListAfter.add(printHelper.formatAndStyleLine(filePath, lineCount, it,
                            isMatch, color, noHeading))
                    }
                }
            }
                lineCount++
        }
        return resultList
    }

    private fun aggregatePrintLinesNoContext(inputStream: InputStream, filePath: String, pattern:CharArray,
                                             color: Boolean, noHeading: Boolean): MutableList<String>
    {
        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (isMatch)
            {
                resultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                            printHelper.getLineWithColoredMatch(pattern, line, color,
                                                searchResult.second), isMatch, color, noHeading))
            }
            lineCount++
        }
        return resultList
    }

    /**This method creates the look-up table delta 1.
     * It contains the shift values for all characters of a search pattern.
     * If we encounter a mismatch, we take the mismatching character from the text T
     * (the text that we compare our pattern against) and look it up in the badCharacterShiftTable.
     * The shift value for that specific character will tell us how far we can skip our pattern ahead.
     * The shift value for characters that are not in the table is always the length of the pattern.
     * */
    fun createBadCharacterShiftTable(pattern: CharArray): MutableMap<Char, Int>
    {
        val badCharacterTable = mutableMapOf<Char, Int>()
        for (i in pattern.indices)
        {
            if (!badCharacterTable.containsKey(pattern[i]))
            {
                badCharacterTable.put(pattern[i], max(1,pattern.size - i - 1))
            }
            else //overwrite shift value of char with the shift value of the char that occurs at a higher index
            {
                badCharacterTable[pattern[i]] = max(1, pattern.size - i - 1)
            }
        }
        return badCharacterTable
    }

    fun setBadCharacterTable(pattern: CharArray){
        badCharacterTable = createBadCharacterShiftTable(pattern)
    }


    //uses the Boyer-Moore-Horspool algorithm to match a line of text with the search pattern
    private fun searchStringInText(pattern: CharArray, line: CharArray):Pair<Boolean, Int>
    {
        val patternLen = pattern.size
        val lineLen = line.size
        var patIndex = patternLen - 1 //we compare the pattern with the line from left to right, but start from the right side of the pattern to look for matches in the line
        var lineIndex = patIndex
        var shiftValue: Int
        var shiftTotal = 0
        var matchedChar = 0

        while(lineLen - shiftTotal >= patternLen )
        {
            if(pattern[patIndex].equals(line[lineIndex]))
            {
                if (patIndex == 0)
                    return Pair(true, lineIndex)
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
        return Pair(false, -1)
    }

    private fun getShiftValue(line: CharArray, lineIndex: Int, patternLen: Int): Int
    {
        return badCharacterTable[line[lineIndex]] ?: patternLen
    }

    fun preprocess(line: String, ignoreCase: Boolean = false): CharArray {
        val result = if (ignoreCase)
            line.lowercase(Locale.getDefault())
        else
            line
        return result.toCharArray()
    }

    //based on the algorithm proposed on: https://stackoverflow.com/questions/620993/determining-binary-text-file-type-in-java
    //this method reads a byte array of the given file and determines the percentage of non-ascii-characters
    //if that percentage surpasses 95 %, the file will be evaluated as binary
    private fun isBinaryFile(path: String): Boolean {

        val inputStream: InputStream = File(path).inputStream()
        var size = inputStream.available()

        if (size > 1024)
            size = 1024
        val data = ByteArray(size)
        inputStream.read(data)
        inputStream.close()

        var ascii = 0
        var nonAscii = 0
        for (i in data.indices) {
            val b = data[i]

            if (b.toInt() == 0x09 || b.toInt() == 0x0A || b.toInt() == 0x0C || b.toInt() == 0x0D)
                ascii++
            else if (b in 0x20..0x7E)
                ascii++
            else
                nonAscii++
        }
        if (nonAscii == 0)
            return false
        else{
            return (nonAscii / size * 100) > 95
        }

    }

}