
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

data class UserInput(val pattern: String, val path: String,
                     val ignoreCase: Boolean, val noHeading: Boolean,
                     val hidden: Boolean, val color: Boolean)
class Search : CliktCommand(invokeWithoutSubcommand = true) {

    private val pattern: String by argument(help = "substring to search for")
    private val path: String by argument(help = "file system path")

    private val ignoreCase by option("-i", "--ignore-case", help= "search case insensitive").flag(default = false)
    private val noHeading by option("--no-heading", help= "prints a single line including the filename for each match, instead of grouping matches by file").flag(default = false)
    private val hidden by option("-h", "--hidden", help= "search hidden files and folders").flag(default = false)
    //private val excludeBinary by option("-b", "--no-binary", help= "excludes binary files").flag(default = false)
    private val color by option("-c", "--color", help= "prints with colors, highlighting the matched phrase in the output").flag(default = false)

    override fun run() {
        //save user inputs in context for subcommands
        val userInput = UserInput(pattern, path, ignoreCase, noHeading, hidden, color)
        currentContext.obj = userInput

        //search only in case there are no subcommands
        val subcommand = currentContext.invokedSubcommand
        if (subcommand == null && !userInput.path.equals(null) && !userInput.pattern.equals(null)) {
            val searcher = Searcher()
            searcher.recursiveFileSearch(userInput.path,
                searcher.preprocess(userInput.pattern, userInput.ignoreCase),
                userInput)
        }

    }
}

class AfterContext : CliktCommand(){

    private val userInput: UserInput by requireObject()
    private val afterContext: Int? by option("-A", "--after-context",
        help= "prints the given number of following lines for each match")
        .int()

    override fun run() {
        val searcher = Searcher()
        searcher.recursiveFileSearch(userInput.path,
            searcher.preprocess(userInput.pattern, userInput.ignoreCase),
            userInput, linesAfter = afterContext)
    }

}
class BeforeContext : CliktCommand() {

    private val userInput: UserInput by requireObject()
    private val beforeContext: Int? by option("-B", "--before-context",
        help= "prints the given number of preceding lines for each match")
        .int()

    override fun run() {
        val searcher = Searcher()
        searcher.recursiveFileSearch(userInput.path,
            searcher.preprocess(userInput.pattern, userInput.ignoreCase),
            userInput, linesBefore = beforeContext)
    }
}

class ContextSearch : CliktCommand() {
    //options
    private val userInput: UserInput by requireObject()
    private val context: Int? by option("-C", "--context",
        help= "prints the number of preceding and following lines for each match")
        .int()

    override fun run() {
        val searcher = Searcher()
        searcher.recursiveFileSearch(userInput.path,
            searcher.preprocess(userInput.pattern, userInput.ignoreCase), //TODO remove duplicate
            userInput, contextLines = context)
    }
}
fun main(args: Array<String>) = Search().subcommands(BeforeContext(),
                                AfterContext(), ContextSearch())
                                .main(args)
