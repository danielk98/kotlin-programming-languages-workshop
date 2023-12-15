
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.math.max


class Searcher {
    var badCharacterTable: Map<Char, Int> = emptyMap<Char, Int>()
    fun recursiveFileSearch(path: String, pattern: CharArray, userInput: UserInput, linesBefore: Int? = null,
                            linesAfter: Int? = null, contextLines: Int? = null){
        val paths: List<Path> = Path(path).listDirectoryEntries()
        for (p in paths) {
            //skip hidden files, unless the option is set
            if (p.isHidden() && !userInput.hidden){
                continue
            }
            if (p.isRegularFile()) {
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

        badCharacterTable = createBadCharacterShiftTable(pattern)
        val inputStream: InputStream = File(filePath).inputStream()
        var resultList: MutableList<String> = mutableListOf()
        //iterates through lines and calls searchStringInText (Boyer Moore Horspool algorithm)
        //depending on subcommands, the required lines will be aggregated by the respective
        // aggregatePrintLines... function
        resultList = when {
            (linesBefore != null)
            -> aggregatePrintLinesBeforeMatch(inputStream, filePath, pattern, color, linesBefore)

            (linesAfter != null)
            -> aggregatePrintLinesAfterMatch(inputStream, filePath, pattern, color, linesAfter)

            (contextLines != null)
            -> aggregatePrintLinesWithContext(inputStream, filePath, pattern, color, contextLines)

            else -> aggregatePrintLinesNoContext(inputStream, filePath, pattern, color)
        }

        printResult(resultList, noHeading)
    }

    private fun aggregatePrintLinesBeforeMatch(
        inputStream: InputStream, filePath: String,
        pattern: CharArray, color: Boolean, linesBefore: Int): MutableList<String>  {

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
        pattern: CharArray, color: Boolean, linesAfter: Int): MutableList<String>  {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultList: MutableList<String> = mutableListOf()
        var matchInPartialResultList = false
        var offset = linesAfter

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val isMatch = searchStringInText(pattern, line)

            if (isMatch){
                partialResultList.add(filePath + ":" + lineCount + ":" + it.toString() + "\n")
                matchInPartialResultList = true
            }
            else
            {
                if (matchInPartialResultList)
                    partialResultList.add(filePath + "-" + lineCount + "-" + it.toString() + "\n")
            }
            lineCount++

            if (partialResultList.count() > offset){
                resultList.addAll(partialResultList)
                partialResultList.clear()
                matchInPartialResultList = false
            }
        }
        return resultList
    }

    private fun aggregatePrintLinesWithContext(
        inputStream: InputStream,filePath: String,
        pattern: CharArray, color: Boolean, contextLines: Int): MutableList<String>  {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultListBefore: MutableList<String> = mutableListOf()
        val partialResultListAfter: MutableList<String> = mutableListOf()
        var listHasMatch = false

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val isMatch = searchStringInText(pattern, line)

            if (!listHasMatch) {
                if (!isMatch) {
                    if (partialResultListBefore.count() > contextLines)
                        partialResultListBefore.removeFirst()
                    partialResultListBefore.add(filePath + "-" + lineCount + "-" + it.toString() + "\n")
                } else {
                    if (partialResultListBefore.count() > contextLines)
                        partialResultListBefore.removeFirst()
                    partialResultListBefore.add(filePath + ":" + lineCount + ":" + it.toString() + "\n")
                    listHasMatch = true
                }
            }
            else
            {
                if (isMatch) {
                    if (partialResultListAfter.count() >= contextLines) {
                        resultList.addAll(partialResultListBefore)
                        resultList.addAll(partialResultListAfter)
                        partialResultListBefore.clear()
                        partialResultListAfter.clear()
                        listHasMatch = false
                    } else
                        partialResultListAfter.add(filePath + ":" + lineCount + ":" + it.toString() + "\n")
                } else {
                    if (partialResultListAfter.count() >= contextLines) {
                        resultList.addAll(partialResultListBefore)
                        resultList.addAll(partialResultListAfter)
                        partialResultListBefore.clear()
                        partialResultListAfter.clear()
                        listHasMatch = false
                    } else
                        partialResultListAfter.add(filePath + "-" + lineCount + "-" + it.toString() + "\n")

                }
            }
                lineCount++
        }
        return resultList
    }

    private fun aggregatePrintLinesNoContext(inputStream: InputStream, filePath: String,
                                             pattern:CharArray, color: Boolean): MutableList<String> {
        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()

        inputStream.bufferedReader().forEachLine {
            val line = preprocess(it)
            val isMatch = searchStringInText(pattern, line)

            if (isMatch) {
                resultList.add(formatAndStyleLine(filePath, lineCount, it.toString(), isMatch, color ))
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
    private fun createBadCharacterShiftTable(pattern: CharArray): MutableMap<Char, Int>
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
    private fun printResult(result: MutableList<String>, noHeading: Boolean){
        for (i in result.indices)
            print(result[i])
        //separates printed lines of different files
        if (noHeading)
            println("--")
    }
    //uses the Boyer-Moore-Horspool algorithm to match a line of text with the search pattern
    private fun searchStringInText(pattern: CharArray, line: CharArray): Boolean
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

    private fun getShiftValue(line: CharArray, lineIndex: Int, patternLen: Int): Int
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

    private fun formatAndStyleLine(path: String, lineCount: Int, line: String,
                           isMatch: Boolean, withColor: Boolean = false): String{
        //anis escape codes
        val reset = "\u001b[0m"
        val purple = "\u001b[35m"  //purple for the filePath
        val green = "\u001b[32m" //green for the lineCount
        val red = "\u001b[31m"  //red for highlighting matches

        val result = when {
            isMatch && withColor -> "$purple$path$reset:$green$lineCount$reset:$line\n"
            !isMatch && withColor -> "$purple$path$reset-$green$lineCount$reset-$line\n"
            isMatch && !withColor -> "$path:$lineCount:$line\n"
            else -> "$path-$lineCount-$line\n"
        }
        return result
    }

}