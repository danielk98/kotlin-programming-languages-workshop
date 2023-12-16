import java.util.regex.Pattern

//This class is used for coloring and printing the output
class PrintHelper {

    //ANSI escape codes
    private val reset = "\u001b[0m"
    private val purple = "\u001b[95m"   //purple for the filePath
    private val green = "\u001b[92m"    //green for the lineCount
    private val red = "\u001b[91m"      //red for highlighting matches

    fun formatAndStyleLine(path: String, lineCount: Int, line: String, isMatch: Boolean,
                           withColor: Boolean = false, noHeading: Boolean = false): String{

        val result: String
        if (noHeading) {
            result = when {
                isMatch && withColor -> ":$green$lineCount$reset:$line\n"
                !isMatch && withColor -> "-$green$lineCount$reset-$line\n"
                isMatch && !withColor -> ":$lineCount:$line\n"
                else -> "-$lineCount-$line\n"
            }
        }
        else {
            result = when {
                isMatch && withColor -> "$purple$path$reset:$green$lineCount$reset:$line\n"
                !isMatch && withColor -> "$purple$path$reset-$green$lineCount$reset-$line\n"
                isMatch && !withColor -> "$path:$lineCount:$line\n"
                else -> "$path-$lineCount-$line\n"
            }
        }
        return result
    }

    //This method splits the line into two separate string on the index of the
    //matching substring to turn
    fun getLineWithColoredMatch(pattern: Pattern, line: CharSequence,
                                color: Boolean, lineIndexMatchStart: MutableList<Int>): String {

        var firstPart: String
        var matchingSubstring: String
        var secondPart: String
        var result: CharSequence = line

        var lineIndexMatchEnd: Int

        var colorOffset = 0
        var resetOffset = 0
        var matchIndex = 0
        for (m in lineIndexMatchStart) {
            matchIndex = m
            matchIndex += colorOffset + resetOffset
            lineIndexMatchEnd =  matchIndex + pattern.pattern().length

            if (color) {
                firstPart = result.subSequence(0, matchIndex).toString()
                matchingSubstring = result.subSequence(matchIndex, lineIndexMatchEnd).toString()
                secondPart = result.subSequence(lineIndexMatchEnd, result.length).toString()

                result = firstPart + red + matchingSubstring + reset + secondPart
                colorOffset += red.length
                resetOffset += reset.length
            }
            else {
                result = result.toString()
            }
        }

            return result.toString()
        }
    fun printResult(result: MutableList<String>){
        for (i in result.indices)
            print(result[i])
        //separates printed lines of different files
        if (result.isNotEmpty())
            println("--")
    }
}