
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.math.max

class Searcher {
    var badCharacterTable: Map<Char, Int> = emptyMap()
    val printHelper = PrintHelper()

    fun recursiveFileSearch(
        path: String, pattern: Pattern, userInput: UserInput, linesBefore: Int? = null,
        linesAfter: Int? = null, contextLines: Int? = null
    ) {
        var paths: List<Path> = emptyList()
        //in case given path is a file
        if (Path(path).isDirectory())
            Files.walk(Paths.get(path))
                .filter { Files.isRegularFile(it) }
                .forEach {
                    if (isBinaryFile(it.toString()) && !userInput.binary){ }
                    else if (it.isHidden() && !userInput.hidden) { }
                    else
                    {
                        searchAllLines(it.toString(), pattern, userInput.noHeading, userInput.color, linesBefore, linesAfter, contextLines)
                    }
                }
        else
            if (isBinaryFile(path) && !userInput.binary){ }
            else if (Path(path).isHidden() && !userInput.hidden) { }
            else
            {
                searchAllLines(path, pattern, userInput.noHeading, userInput.color, linesBefore, linesAfter, contextLines)
            }
    }

    private fun searchAllLines(
        filePath: String, pattern: Pattern, noHeading: Boolean,
        color: Boolean, linesBefore: Int?, linesAfter: Int?, contextLines: Int?
    ) {

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
        inputStream: InputStream, filePath: String, pattern: Pattern, color: Boolean,
        noHeading: Boolean, linesBefore: Int
    ): MutableList<String> {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultList: MutableList<String> = mutableListOf()

        inputStream.bufferedReader().forEachLine {
            val line = preprocessLine(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (!isMatch) {
                if (partialResultList.count() > linesBefore) {
                    partialResultList.removeFirst()
                }
                partialResultList.add(
                    printHelper.formatAndStyleLine(
                        filePath,
                        lineCount,
                        it,
                        isMatch,
                        color,
                        noHeading
                    )
                )
            } else {
                if (partialResultList.count() > linesBefore) {
                    partialResultList.removeFirst()
                }
                partialResultList.add(
                    printHelper.formatAndStyleLine(
                        filePath,
                        lineCount,
                        printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                        isMatch,
                        color,
                        noHeading
                    )
                )
                resultList.addAll(partialResultList)
                partialResultList.clear()
            }
            lineCount++
        }
        return resultList
    }

    private fun aggregatePrintLinesAfterMatch(
        inputStream: InputStream, filePath: String,
        pattern: Pattern, color: Boolean, noHeading: Boolean, linesAfter: Int
    ): MutableList<String> {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultList: MutableList<String> = mutableListOf()
        var matchInPartialResultList = false

        inputStream.bufferedReader().forEachLine {
            val line = preprocessLine(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (isMatch) {
                partialResultList.add(
                    printHelper.formatAndStyleLine(
                        filePath, lineCount,
                        printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                        isMatch, color, noHeading
                    )
                )
                matchInPartialResultList = true
            } else {
                if (matchInPartialResultList)
                    partialResultList.add(
                        printHelper.formatAndStyleLine(
                            filePath,
                            lineCount,
                            it,
                            isMatch,
                            color,
                            noHeading
                        )
                    )
            }
            lineCount++

            if (partialResultList.count() > linesAfter) {
                resultList.addAll(partialResultList)
                partialResultList.clear()
                matchInPartialResultList = false
            }
        }
        return resultList
    }

    private fun aggregatePrintLinesWithContext(
        inputStream: InputStream, filePath: String, pattern: Pattern,
        color: Boolean, noHeading: Boolean, contextLines: Int
    ): MutableList<String> {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        val partialResultListBefore: MutableList<String> = mutableListOf()
        val partialResultListAfter: MutableList<String> = mutableListOf()
        var listHasMatch = false

        inputStream.bufferedReader().forEachLine {
            val line = preprocessLine(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (!listHasMatch) {
                if (!isMatch) {
                    if (partialResultListBefore.count() > contextLines) {
                        partialResultListBefore.removeFirst()
                    }
                    partialResultListBefore.add(
                        printHelper.formatAndStyleLine(
                            filePath,
                            lineCount,
                            it,
                            isMatch,
                            color,
                            noHeading
                        )
                    )
                } else {
                    if (partialResultListBefore.count() > contextLines) {
                        partialResultListBefore.removeFirst()
                    }
                    partialResultListBefore.add(
                        printHelper.formatAndStyleLine(
                            filePath, lineCount,
                            printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                            isMatch, color, noHeading
                        )
                    )
                    listHasMatch = true
                }
            } else {
                if (isMatch) {
                    if (partialResultListAfter.count() >= contextLines) {
                        resultList.addAll(partialResultListBefore)
                        resultList.addAll(partialResultListAfter)
                        partialResultListBefore.clear()
                        partialResultListAfter.clear()
                        listHasMatch = false
                    } else {
                        partialResultListAfter.add(
                            printHelper.formatAndStyleLine(
                                filePath, lineCount,
                                printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                                isMatch, color, noHeading
                            )
                        )
                    }
                } else {
                    if (partialResultListAfter.count() >= contextLines) {
                        resultList.addAll(partialResultListBefore)
                        resultList.addAll(partialResultListAfter)
                        partialResultListBefore.clear()
                        partialResultListAfter.clear()
                        listHasMatch = false
                    } else {
                        partialResultListAfter.add(
                            printHelper.formatAndStyleLine(
                                filePath, lineCount, it,
                                isMatch, color, noHeading
                            )
                        )
                    }
                }
            }
            lineCount++
        }
        return resultList
    }

    private fun aggregatePrintLinesNoContext(
        inputStream: InputStream, filePath: String, pattern: Pattern,
        color: Boolean, noHeading: Boolean
    ): MutableList<String> {
        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()

        inputStream.bufferedReader().forEachLine {
            val line = preprocessLine(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (isMatch) {
                resultList.add(
                    printHelper.formatAndStyleLine(
                        filePath, lineCount,
                        printHelper.getLineWithColoredMatch(
                            pattern, line, color,
                            searchResult.second
                        ), isMatch, color, noHeading
                    )
                )
            }
            lineCount++
        }
        return resultList
    }

    /** --***Not-used***-- This method creates the look-up table delta 1.
     * It contains the shift values for all characters of a search pattern.
     * If we encounter a mismatch, we take the mismatching character from the text T
     * (the text that we compare our pattern against) and look it up in the badCharacterShiftTable.
     * The shift value for that specific character will tell us how far we can skip our pattern ahead.
     * The shift value for characters that are not in the table is always the length of the pattern.
     * */
    fun createBadCharacterShiftTable(pattern: CharArray): MutableMap<Char, Int> {
        val badCharacterTable = mutableMapOf<Char, Int>()
        for (i in pattern.indices) {
            if (!badCharacterTable.containsKey(pattern[i])) {
                badCharacterTable.put(pattern[i], max(1, pattern.size - i - 1))
            } else //overwrite shift value of char with the shift value of the char that occurs at a higher index
            {
                badCharacterTable[pattern[i]] = max(1, pattern.size - i - 1)
            }
        }
        return badCharacterTable
    }

    fun setBadCharacterTable(pattern: CharArray) {
        badCharacterTable = createBadCharacterShiftTable(pattern)
    }


    //
    private fun searchStringInText(pattern: Pattern, line: CharSequence): Pair<Boolean, MutableMap<Int,Int>> {
        /*val patternLen = pattern.pattern.length
        val lineLen = line.length
        var patIndex = patternLen - 1 //we compare the pattern with the line from left to right, but start from the right side of the pattern to look for matches in the line
        var lineIndex = patIndex
        var shiftValue: Int
        var shiftTotal = 0
        var matchedChar = 0
         */
        //val pat = Pattern.compile(pattern)
        //while(lineLen - shiftTotal >= patternLen )
        //{
        val matcher = pattern.matcher(line)
        var matchIndices: MutableMap<Int,Int> = emptyMap<Int,Int>().toMutableMap()
        while (matcher.find()) {
            matchIndices.put(matcher.start(), matcher.end())
        }
        if (matchIndices.isEmpty())
            return Pair(false, matchIndices)
        else
            return Pair(true, matchIndices)
        //return Pair(false, -1)
    }

    private fun getShiftValue(line: CharArray, lineIndex: Int, patternLen: Int): Int
    {
        return badCharacterTable[line[lineIndex]] ?: patternLen
    }

    fun preprocess(line: String, ignoreCase: Boolean = false): Pattern {
        val result = if (ignoreCase)
            Pattern.compile(line, Pattern.CASE_INSENSITIVE)
        else
            Pattern.compile(line)
        return result
    }

    fun preprocessLine(line: String): CharSequence{
        val result: CharSequence
        result = line
        return result
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