
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.*
import kotlin.math.max

class Searcher {
    var badCharacterTable: Map<Char, Int> = emptyMap()
    val printHelper = PrintHelper()

    fun recursiveFileSearch(
        path: String, pattern: Pattern, userInput: UserInput, linesBefore: Int? = null,
        linesAfter: Int? = null, contextLines: Int? = null, optionBased: Boolean = false
    ) {
        var paths: List<Path> = emptyList()
        //in case given path is a file
        if (Path(path).isDirectory())
            paths = Path(path).listDirectoryEntries()
        else {
            if (Path(path).isHidden() && !userInput.hidden) { }
            else if (isBinaryFile(path) && !userInput.binary) { }
            else if (Path(path).isRegularFile()) {
                searchAllLines(path, pattern, userInput.noHeading, userInput.color,
                    linesBefore, linesAfter, contextLines, optionBased)
            }
        }

        for (p in paths) {
            //skip hidden files, unless the option is set
            if (p.isHidden() && !userInput.hidden) { }
            //skip binary files, unless the option is set
            else if (p.isRegularFile()) {
                if (isBinaryFile(p.toString()) && !userInput.binary) { }
                else
                searchAllLines(p.toString(), pattern, userInput.noHeading, userInput.color,
                    linesBefore, linesAfter, contextLines, optionBased)
            }
            else if (p.isDirectory()) {
                recursiveFileSearch(p.toString(), pattern, userInput, linesBefore, linesAfter, contextLines)
            }
        }
    }

    private fun searchAllLines(
        filePath: String, pattern: Pattern, noHeading: Boolean,
        color: Boolean, linesBefore: Int?, linesAfter: Int?, contextLines: Int?,
        optionBased: Boolean
    ) {
        
        //badCharacterTable = createBadCharacterShiftTable(pattern)
        val inputStream: InputStream = File(filePath).inputStream()
        val resultList: MutableList<String>
        if (!optionBased) {
            //iterates through lines and calls searchStringInText
            //depending on subcommands, the required lines will be aggregated by the respective
            // aggregatePrintLines... function
                resultList = when {
                (linesBefore != null)
                -> aggregatePrintLinesBeforeMatch(inputStream, filePath, pattern, color, noHeading, linesBefore)    //subcommand: before-context

                (linesAfter != null)
                -> aggregatePrintLinesAfterMatch(inputStream, filePath, pattern, color, noHeading, linesAfter)      //subcommand: after-context

                (contextLines != null)
                -> aggregatePrintLinesWithContext(inputStream, filePath, pattern, color, noHeading, contextLines)   //subcommand: context-search

                else -> aggregatePrintLinesNoContext(inputStream, filePath, pattern, color, noHeading)
            }
        }
        else {
            resultList = when {
                (contextLines != null && contextLines != 0)
                -> aggregatePrintLinesWithContext(inputStream, filePath, pattern, color, noHeading, contextLines)  //option -C, --context-search
                //option based context search: -A --after-context and -B --before-context
                else -> aggregatePrintLinesForOptionBasedContextSearch(inputStream, filePath, pattern, color, noHeading, linesBefore, linesAfter
                )
            }
        }

        printHelper.printResult(resultList)
    }

    //used for subcommand before-context
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

    //used for subcommand after-context
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
                        printHelper.formatAndStyleLine(filePath, lineCount, it, isMatch,
                            color, noHeading))
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

    //used for subcommand --context-search AND option -C --context
    private fun aggregatePrintLinesWithContext(
        inputStream: InputStream, filePath: String, pattern: Pattern,
        color: Boolean, noHeading: Boolean, contextLines: Int
    ): MutableList<String> {

        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        var partialResultList: MutableList<String> = mutableListOf()
        var listHasMatch = false
        var lastMatch = 0

        inputStream.bufferedReader().forEachLine {
            val line = preprocessLine(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (!listHasMatch){
                if (partialResultList.count() > contextLines)
                    partialResultList.removeFirst()
                if (isMatch){
                    partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                        printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                        isMatch, color, noHeading))

                    listHasMatch = true
                }
                else
                {
                    partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                        it, isMatch, color, noHeading))
                }
            }
            else
            {
                if (lastMatch == contextLines){
                    resultList.addAll(partialResultList)
                    resultList.add("--\n")

                    partialResultList.clear()
                    listHasMatch = false
                    lastMatch = 0
                }
                else
                {
                    if (isMatch)
                    {
                        partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                            printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                            isMatch, color, noHeading))

                        lastMatch = 0
                    }
                    else
                    {
                        partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                            it, isMatch, color, noHeading))

                        lastMatch++
                    }
                }

            }
            lineCount++
        }
        return resultList
    }

    //used for simple search
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

    //used for option -A and -B
    private fun aggregatePrintLinesForOptionBasedContextSearch(inputStream: InputStream, filePath: String, pattern: Pattern,
        color: Boolean, noHeading: Boolean, linesBefore: Int?, linesAfter: Int?): MutableList<String>
    {
        var lineCount = 1
        val resultList: MutableList<String> = mutableListOf()
        var partialResultList: MutableList<String> = mutableListOf()
        var listHasMatch = false
        var lastMatch = 0
        val beforeOffset = linesBefore?: 0
        val afterOffset = linesAfter?: 0

        inputStream.bufferedReader().forEachLine {
            val line = preprocessLine(it)
            val searchResult = searchStringInText(pattern, line)
            val isMatch = searchResult.first

            if (!listHasMatch){
                if (partialResultList.count() > beforeOffset)
                    partialResultList.removeFirst()
                if (isMatch){
                    partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                        printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                        isMatch, color, noHeading))

                    listHasMatch = true
                }
                else
                {
                    partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                        it, isMatch, color, noHeading))
                }
            }
            else
            {
                if (lastMatch == afterOffset){
                    resultList.addAll(partialResultList)
                    resultList.add("--\n")

                    partialResultList.clear()
                    listHasMatch = false
                    lastMatch = 0
                }
                else
                {
                    if (isMatch)
                    {
                        partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                            printHelper.getLineWithColoredMatch(pattern, line, color, searchResult.second),
                            isMatch, color, noHeading))

                        lastMatch = 0
                    }
                    else
                    {
                        partialResultList.add(printHelper.formatAndStyleLine(filePath, lineCount,
                            it, isMatch, color, noHeading))

                        lastMatch++
                    }
                }

            }
            lineCount++
        }
        return resultList

    }

    private fun searchStringInText(pattern: Pattern, line: CharSequence): Pair<Boolean, MutableMap<Int,Int>> {

        val matcher = pattern.matcher(line)
        var matchIndices: MutableMap<Int,Int> = emptyMap<Int,Int>().toMutableMap()
        while (matcher.find()) {
            matchIndices.put(matcher.start(), matcher.end())
        }
        if (matchIndices.isEmpty())
            return Pair(false, matchIndices)
        else
            return Pair(true, matchIndices)
    }

    //used to preprocess the search pattern
    fun preprocess(line: String, ignoreCase: Boolean = false): Pattern {
        return if (ignoreCase)
            Pattern.compile(line, Pattern.CASE_INSENSITIVE)
        else
            Pattern.compile(line)
    }

    fun preprocessLine(line: String): CharSequence{
        val result: CharSequence
        result = line
        return result
    }

    //based on the algorithm proposed on: https://stackoverflow.com/questions/620993/determining-binary-text-file-type-in-java
    //this method reads a byte array of the given file and determines the percentage of control ascii-characters
    //or asciis from the extended set. If that percentage surpasses 50 %, the file will be evaluated as binary.
    private fun isBinaryFile(path: String): Boolean {

        val inputStream: InputStream = File(path).inputStream()
        var size = inputStream.available()

        if (size > 1024)
            size = 1024
        val data = ByteArray(size)
        inputStream.read(data)
        inputStream.close()

        var ascii = 0
        var other = 0

        for (i in data.indices) {
            val b: Byte = data[i]

            if (b.toInt() == 0x09 || b.toInt() == 0x0A || b.toInt() == 0x0C || b.toInt() == 0x0D)
                ascii++
            else if (b in 0x20..0x7E)
                ascii++
            else
                other++
        }

        if (other == 0)
            return false
        else{
            return 100 * other / (ascii + other) > 50;
        }

    }
    /** --***Not-used anymore***--
     * This method creates the look-up table delta 1.
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

    private fun getShiftValue(line: CharArray, lineIndex: Int, patternLen: Int): Int
    {
        return badCharacterTable[line[lineIndex]] ?: patternLen
    }

}