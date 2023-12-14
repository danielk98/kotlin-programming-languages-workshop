
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int

data class userInput(val pattern: String, val path: String,
                     val ignoreCase: Boolean, val noHeading: Boolean, val hidden: Boolean)
class Search : CliktCommand(invokeWithoutSubcommand = true) {

    private val pattern: String by argument(help = "substring to search for")
    private val path: String by argument(help = "file system path")

    private val ignoreCase by option("-i", "--ignore-case", help= "search case insensitive").flag()
    private val noHeading by option("--no-heading", help= "prints a single line including the filename for each match, instead of grouping matches by file").flag()
    private val hidden by option("-h", "--hidden", help= "search hidden files and folders").flag()

    override fun run() {
        //save user inputs in context for subcommands
        val userInput = userInput(pattern, path, ignoreCase, noHeading, hidden)
        currentContext.obj = userInput

        //search only in case there are no subcommands
        val subcommand = currentContext.invokedSubcommand
        if (subcommand == null && !userInput.path.equals(null) && !userInput.pattern.equals(null)) {
            val searcher = Searcher()
            searcher.recursiveFileSearch(userInput.path,
                searcher.preprocess(userInput.pattern, userInput.ignoreCase),
                userInput.noHeading,
                userInput.hidden)
        }

    }
}

class AfterContextSearch : CliktCommand(){

    val userInput: userInput by requireObject()
    val afterContext: Int? by option("-A", "--after-context", help= "prints the given number of following lines for each match").int().default(1)


    override fun run() {
        val searcher = Searcher()

    }

}
class BeforeContextSearch : CliktCommand() {

    val userInput: userInput by requireObject()
    val beforeContext: Int? by option("-B", "--before-context",
        help= "prints the given number of preceding lines for each match")
        .int().default(1)



    override fun run() {
        val searcher = Searcher()

    }
}

class ContextSearch : CliktCommand() {
    //options
    val userInput: userInput by requireObject()
    val context: Int? by option("-C", "--context",
        help= "prints the number of preceding and following lines for each match")
        .int().default(1)


    override fun run() {
        val searcher = Searcher()

    }
}
fun main(args: Array<String>) = Search()
                                .main(args)
/*
val afterContext: Int? by option("-A", "--after-context", help= "prints the given number of following lines for each match").int().default(1)
val beforeContext: Int? by option("-B", "--before-context", help= "prints the given number of preceding lines for each match").int().default(1)
val context: Int? by option("-C", "--context", help= "prints the number of preceding and following lines for each match").int().default(1)
val color by option("-c", "--color", help= "prints with colors, highlighting the matched phrase in the output").flag()
val hidden by option("-h", "--hidden", help= "search hidden files and folders").flag()
val help by option(help="prints this message").flag()
val ignoreCase by option("-i", "--ignore-case", help= "search case insensitive").flag()
val noHeading by option("--no-heading", help= "prints a single line including the filename for each match, instead of grouping matches by file").flag()
//val exit: Int? by option(help = "exit program with 0").int().default(0)*/