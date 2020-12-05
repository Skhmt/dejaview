import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.web.WebView
import javafx.stage.Stage
import netscape.javascript.JSObject
import java.io.File
import java.io.InputStream
import java.net.URL
import kotlin.system.exitProcess

class DeJaView(primaryStage: Stage, args: DJVArgs, backend: DJVApi) {
    init {
        // turn on anti-aliasing for fonts
        System.setProperty("prism.lcdtext", "false")

        val browser = WebView()
        primaryStage.apply {
            scene = Scene(browser)

            // application icon
            if (args.iconRelativePath.isNotEmpty()) icons.add(Image(resourceStream(args.iconRelativePath)))
        }
        browser.apply {
            // disable right-clicking
            if (args.disableRightClick) isContextMenuEnabled = false

            // set up browser and API
            engine.loadWorker.stateProperty().addListener { _, _, newState ->
                when (newState) {
                    Worker.State.RUNNING -> {
                        // load the backend
                        val window = browser.engine.executeScript("window") as JSObject
                        backend.init(primaryStage, window)
                        window.setMember(args.apiVarName, backend)

                        // modify the console to print to the java console
                        modConsole(window)
                    }
                    Worker.State.SUCCEEDED -> {
                        primaryStage.show()
                    }
                    Worker.State.FAILED -> {
                        println("Failed to initialize browser")
                        exitProcess(0)
                    }
                    else -> {}
                }
            }

            // load the entry page
            if (args.pageRelativePath.isNotEmpty()) engine.load(resource(args.pageRelativePath).toString())
            else {
                println("No entry path given - loading test page")
                val testPage = """
                    <html lang="en"><head><meta charset="UTF-8"></head><body><h1>test page</h1></body></html>
                """.trimIndent()
                engine.loadContent(testPage)
            }
        }
    }

    /**
     * Modifies window.console.log/info/warn/error to output to the java console
     */
    private fun modConsole(window: JSObject) {
        val red = "\u001b[91m"
        val green = "\u001B[92m"
        val yellow = "\u001b[93m"
        val blue = "\u001B[94m"
        val white = "\u001B[97m"
        val redBG = "\u001B[101m$white"
        val yellowBG = "\u001B[103m$white"
        val clear = "\u001B[0m"

        fun parseConsole(arg: Any?): String {
            if (arg == null) return "null"

            when ((arg as Any)) {
                is String -> return "$green\"$clear$arg$green\"$clear"
                is JSObject -> {
                    // if the "apply()" in `arg` is a function vs if it's a string (undefined)
                    val argApply = (arg as JSObject).getMember("apply")
                    return if (argApply is JSObject) { // `arg` is a function
                        // TODO format function log output
                        // (arg as JSObject).toString().replace("\n","")
                        (arg as JSObject).toString()
                    } else { // `arg` is a plain object
                        val wJSON = window.getMember("JSON") as JSObject
                        wJSON.call("stringify", arg) as String
                    }
                }
                // kotlin.Boolean, kotlin.Int, kotlin.Double
                else -> return (arg as Any).toString()
            }
        }

        class JSConsole {
            fun log(arg: Any?) = println("$green=>$clear ${parseConsole(arg)}")
            fun info(arg: Any?) = println("$blue?>$clear ${parseConsole(arg)}")
            fun warn(arg: Any?) = println("$yellowBG!$clear$yellow>$clear ${parseConsole(arg)}")
            fun error(arg: Any?) = println("${redBG}x$clear$red>$clear ${parseConsole(arg)}")
        }
        window.setMember("console", JSConsole())
    }

    /**
     * Gets a resource relative to the .jar/.exe
     */
    private fun resource(relativePath: String): URL {
        return {}.javaClass.getResource(relativePath)
    }

    /**
     * Gets a resource relative to the .jar/.exe as a stream
     */
    private fun resourceStream(relativePath: String): InputStream {
        return {}.javaClass.getResourceAsStream(relativePath)
    }

    /**
     * Gets the path to the directory the .jar/.exe is currently running in
     */
    private fun appPath(): String {
        return File(".").canonicalPath
    }
}

/**
 * Arguments data class for DeJaView
 */
data class DJVArgs (
        val pageRelativePath: String = "",
        val iconRelativePath: String = "",
        val apiVarName: String = "_api",
        val disableRightClick: Boolean = true,
)

/**
 * For creating an API
 */
open class DJVApi {
    lateinit var stage: Stage
    lateinit var win: JSObject

    fun init(stage: Stage, win: JSObject) {
        this.stage = stage
        this.win = win
    }
}

/**
 * Allows JSObject to be called as a js function for use in callback functions
 */
operator fun JSObject.invoke(vararg args: Any) {
    call("call", null, *args)
}
