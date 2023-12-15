//This class is used for coloring and printing the output
class PrintHelper {

    fun formatAndStyleLine(path: String, lineCount: Int, line: String,
                                   isMatch: Boolean, withColor: Boolean = false): String{
        //ANSI escape codes
        val reset = "\u001b[0m"
        val purple = "\u001b[95m"  //purple for the filePath
        val green = "\u001b[92m" //green for the lineCount

        val result = when {
            isMatch && withColor -> "$purple$path$reset:$green$lineCount$reset:$line\n"
            !isMatch && withColor -> "$purple$path$reset-$green$lineCount$reset-$line\n"
            isMatch && !withColor -> "$path:$lineCount:$line\n"
            else -> "$path-$lineCount-$line\n"
        }
        return result
    }

    //This method splits the line into two separate string on the index of the
    //matching substring to turn
    fun getLineWithColoredMatch(pattern: CharArray, line: CharArray,
                                        color: Boolean, lineIndexMatchStart: Int): String{
        //ANSI escape codes
        val reset = "\u001b[0m"
        val red = "\u001b[91m"  //red for highlighting matches

        val lineIndexMatchEnd = lineIndexMatchStart + pattern.size -1

        val firstPart: String
        val matchingSubstring: String
        val secondPart: String
        val result: String

        if (color) {
            firstPart = line.copyOfRange(0, lineIndexMatchStart).concatToString()
            matchingSubstring = line.copyOfRange(lineIndexMatchStart, lineIndexMatchEnd + 1).concatToString()
            secondPart = line.copyOfRange(lineIndexMatchEnd + 1, line.size).concatToString()

            result = firstPart + red + matchingSubstring + reset + secondPart
        }
        else {
            result = line.concatToString()
        }
        return result
    }
    fun printResult(result: MutableList<String>, noHeading: Boolean){
        for (i in result.indices)
            print(result[i])
        //separates printed lines of different files
        if (noHeading)
            println("--")
    }
}