import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.*

val ai by lazy {
    OpenAI(token = "<openai-token>", LoggingConfig(logLevel = LogLevel.None))
}

const val CLEAN = "\b \b"
const val NL = "\r\n"

fun main() = withRawMode {
    var requestJob: Job? = null
    val console = System.console()
    val reader = console.reader()
    val history = mutableListOf<ChatMessage>()
    var lastMessage = ""

    withPrompt {
        acceptUserInput(-1)

        while (true) {
            val input = withContext(Dispatchers.IO) {
                reader.read()
            }

            if (input == 3 || input == -1) {
                // 3 stands for Ctrl+C, end of execution
                // -1 - EOF
                break
            }

            requestJob?.run {
                cancelAndJoin()

                history.add(ChatMessage(ChatRole.Assistant, lastMessage))
                lastMessage = ""

                print(NL)
            }

            val prompt = acceptUserInput(input) ?: continue

            requestJob = launch {
                displayAIMessageAnimation()

                history.add(ChatMessage(ChatRole.User, prompt))

                ai.chatCompletions(
                    ChatCompletionRequest(
                        model = ModelId("gpt-3.5-turbo"),
                        messages = history,
                        n = 1,
                    )
                ).collect { chunk ->
                    chunk.choices.singleOrNull()?.let { choice ->
                        if (choice.finishReason != null) {
                            history.add(ChatMessage(ChatRole.Assistant, lastMessage))
                            lastMessage = ""

                            acceptUserInput(-1)
                        } else {
                            val content = choice.delta.content ?: ""

                            lastMessage += content

                            displayAIMessageChunk(content)
                        }
                    }
                }
            }.apply {
                invokeOnCompletion {
                    requestJob = null
                }
            }
        }
    }

    requestJob?.cancelAndJoin()
}

class Prompt {
    private var inputBuffer = ""
    private var accepting = false

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

        if (input == 13) { // enter
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

fun withRawMode(body: suspend CoroutineScope.() -> Unit) {
    runBlocking {
        try {
            execAndWait("/bin/sh", "-c", "stty raw </dev/tty")
            body(this)
            ai.close()
        } finally {
            execAndWait("/bin/sh", "-c", "stty sane </dev/tty")
        }
    }
}

fun execAndWait(vararg cmd: String) {
    Runtime.getRuntime().exec(cmd).waitFor()
}
