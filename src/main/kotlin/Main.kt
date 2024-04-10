import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.*

/*
 * This object will perform actual network calls for OpenAI API.
 * Uses API from this library: https://github.com/aallam/openai-kotlin
 *
 * It is declared lazily, meaning that it will only be instantiated on first access.
 * Using Kotlin delegates: https://kotlinlang.org/docs/delegated-properties.html
 *
 * Place your OpenAI API token here in token parameter.
 * You can get it here: https://platform.openai.com/api-keys
 *
 * It uses Ktor Client under the hood: https://ktor.io
 * https://ktor.io/docs/client-create-new-application.html
 */
val ai by lazy {
    OpenAI(token = "<openai-token>", LoggingConfig(logLevel = LogLevel.None))
}

// Print this to clear the last character printed in the terminal
// first '\b' set the caret one character back to the beginning;
// second, we print a whitespace to overwrite the previous character;
// and third, move the caret back again
const val CLEAN = "\b \b"

// proper new line string for raw terminal mode.
// resets caret to the beginning of the line, then prints a new line
const val NL = "\r\n"

// the entrypoint for the application
// starts with setting raw mode, see `withRawMode` function for details
fun main() = withRawMode {
    // maybe contains a job for the current network call for AI API, if any
    var requestJob: Job? = null

    // get the console object of the current system if any
    // (may not be present, see https://stackoverflow.com/questions/26470972/trying-to-read-from-the-console-in-java)
    // in IDE and code editor, use 'run.sh' file to run the app, otherwise you'll get an error here
    val console = System.console()
        ?: error("Console is not present, use ./run.sh to run the app") // Kotlin's 'Elvis' operator for 'if null then' code

    // get the Reader for the console to read user's input
    val reader = console.reader()

    // hold the whole history of the chat, user's and ChatGPT's messages
    val history = mutableListOf<ChatMessage>()

    // property that holds the ChatGPT's last message, that chunks are currently streaming, if any
    var lastMessage = ""

    // `withPrompt` provides a 'Prompt' object in scope
    // (https://kotlinlang.org/docs/scope-functions.html#context-object-this-or-it)
    // this is used for high-level interaction with the terminal
    withPrompt {
        // -1 will just display the prompt for the user
        acceptUserInput(-1)

        // loop until terminated
        while (true) {
            // 'reader.read()' is a blocking IO call.
            // So if we just call, I will block ALL the coroutines that run on the same thread.
            // To overcome this, we use a special dispatcher that can execute blocking IO code
            // somewhere else and return the result to us.
            // Until the result is returned - we suspend
            // https://www.baeldung.com/kotlin/io-and-default-dispatcher
            val input = withContext(Dispatchers.IO) {
                // read one ASCII symbol from the user's input.
                // returns the code for that symbol
                reader.read()
            }

            if (input == 3 || input == -1) {
                // 3 stands for Ctrl+C, end of execution
                // -1 - EOF
                break
            }

            // if there is a running network job, cancel it,
            // wait until it's canceled and update the state accordingly
            requestJob?.run {
                cancelAndJoin()

                // add last assistant's message to the history
                history.add(ChatMessage(ChatRole.Assistant, lastMessage))
                lastMessage = ""

                print(NL)
            }

            // Handle user input
            // If there is nothing to send to AI, continue loop
            val prompt = acceptUserInput(input) ?: continue

            // launch a new coroutine that sends user's message to the API.
            // save its job to requestJob property
            requestJob = launch {
                // launch waiting animation
                displayAIMessageAnimation()

                // add user's message to history
                history.add(ChatMessage(ChatRole.User, prompt))

                // make the call for OpenAI API
                // uses Kotlin FLows: https://kotlinlang.org/docs/flow.html
                ai.chatCompletions(
                    ChatCompletionRequest(
                        model = ModelId("gpt-3.5-turbo"),
                        messages = history,
                        n = 1, // expect only one choice
                    )
                ).collect { chunk -> // collects results from OpenAI when they arrive one-by-one.
                    // we requested only one choice
                    chunk.choices.singleOrNull()?.let { choice ->
                        // check for the last message
                        if (choice.finishReason != null) {
                            // finish message and put it in the history
                            history.add(ChatMessage(ChatRole.Assistant, lastMessage))
                            lastMessage = ""

                            // display empty prompt
                            acceptUserInput(-1)
                        } else {
                            // part of the OpenAI answer
                            val content = choice.delta.content ?: ""

                            lastMessage += content

                            // display this part
                            displayAIMessageChunk(content)
                        }
                    }
                }
            }.apply {
                // when the call is done, clear 'requestJob'
                invokeOnCompletion {
                    requestJob = null
                }
            }
        }
    }

    // whe the app quits - clear resources
    requestJob?.cancelAndJoin()
}

// Prompt class that handles all user and AI interactions with the console
class Prompt {
    // current user's input
    private var inputBuffer = ""
    // whether user starter to type after last sent message
    private var accepting = false

    // accept next char from the terminal
    fun acceptUserInput(input: Int): String? {
        if (!accepting) {
            print("${NL}Prompt :> ")
            accepting = true
        }

        if (input == -1) { // do nothing
            return null
        }

        if (input == 8 || input == 127) { // backspace or delete
            if (inputBuffer.isNotEmpty()) {
                inputBuffer = inputBuffer.dropLast(1)
                print(CLEAN)
            }

            return null
        }

        if (input == 13) { // enter (reset caret symbol actually)
            val result = inputBuffer
            inputBuffer = ""

            return if (result.isNotBlank()) {
                accepting = false
                print("${NL}ChatGPT :< ")

                result
            } else {
                null
            }
        }

        if (input !in 32..127) { // non-printable characters
            return null
        }

        val char = input.toChar()

        inputBuffer += char
        print(char)

        return null
    }

    // same job for waiting animation
    private var animationJob: Job? = null
    private var animationCharLen = 0

    fun CoroutineScope.displayAIMessageAnimation() {
        animationJob = launch {
            val initMessage = "Requesting "
            print(initMessage)
            animationCharLen = initMessage.length

            var i = 0
            while (true) {
                delay(200)

                i = (i + 1) % 4

                if (i == 0) {
                    repeat(3) {
                        print(CLEAN)
                        animationCharLen--
                    }
                } else {
                    print(".")
                    animationCharLen++
                }
            }
        }.apply {
            invokeOnCompletion {
                animationJob = null
            }
        }
    }

    suspend fun displayAIMessageChunk(chunk: String) {
        animationJob?.run {
            cancelAndJoin()
            print(CLEAN.repeat(animationCharLen))
            animationCharLen = 0
        }

        print(chunk.replace("\n", NL))
    }

    suspend fun close() {
        animationJob?.cancelAndJoin()
    }
}

suspend fun withPrompt(body: suspend Prompt.() -> Unit) {
    Prompt().apply {
        body()

        close()
    }
}

// raw mode for the console
// This implementation only works on UNIX systems
// https://stackoverflow.com/questions/13104460/confusion-about-raw-vs-cooked-terminal-modes
// https://www.gnu.org/software/mit-scheme/documentation/stable/mit-scheme-ref/Terminal-Mode.html#:~:text=In%20raw%20mode%2C%20characters%20are,terminal%20by%20the%20operating%20system.
// https://stackoverflow.com/questions/1066318/how-to-read-a-single-char-from-the-console-in-java-as-the-user-types-it
fun withRawMode(body: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        try {
            execAndWait("/bin/sh", "-c", "stty raw </dev/tty")
            body(this)
            ai.close() // close the client before finishing the program
        } finally {
            // no matter error, fix shell mode back to sane
            execAndWait("/bin/sh", "-c", "stty sane </dev/tty")
        }
    }
}

// executes a shell command from JVM process
fun execAndWait(vararg cmd: String) {
    Runtime.getRuntime().exec(cmd).waitFor()
}
