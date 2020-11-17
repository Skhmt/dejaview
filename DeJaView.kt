import javafx.application.Platform
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
    private val testPage = """
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Title</title>
            <style>
                body {
                    margin: 0;
                    padding: 10;
                }
                .grid {
                    display: grid;
                    padding: 20px;
                    grid-template-columns: 1fr 2fr 3fr;
                    margin-top: 0;
                    padding-top: 0;
                }
                textarea {
                    height: 8em;
                }
            </style>
        </head>
        <body>

        <div id="useragent"></div>
        
        <input type="text" id="output" size="100">
        
        <div class="grid">
            <strong></strong>
            <strong>frontend (javascript)</strong>
            <strong>backend (kotlin)</strong>
            
            <button onclick="dirChooser()">Dir Chooser</button>
            <textarea>
        _api.openDirChooser(dir => {
            document.getElementById('output').value = dir;
        });
            </textarea>
            <textarea>
        fun openDirChooser(callback: JSObject) {
            val dirChooser = DirectoryChooser()
            val selectedDir = dirChooser.showDialog(stage)
            win(callback(selectedDir.absolutePath))
        }
            </textarea>
            
            <button onclick="fileChooser()">File Chooser</button>
            <textarea>
        _api.openFileChooser(dir => {
            document.getElementById('output').value = dir;
        });
            </textarea>
            <textarea>
        fun openFileChooser(callback: JSObject) {
            val fileChooser = FileChooser()
            val selectedFile = fileChooser.showOpenDialog(stage) // or showSaveDialog
            win(callback(selectedFile.absolutePath))
        }
            </textarea>
        
            <button onclick="helloWorld()">Hello World</button>
            <textarea>
        document.getElementById('output').value = _api.helloWorld();
            </textarea>
            <textarea>
        fun helloWorld(): String {
            return "Hello World"
        }
            </textarea>
        
            <button onclick="adder()">1+2</button>
            <textarea>
        _api.adderCallback(1,2,sum => {
            document.getElementById('output').value = sum;
        })
            </textarea>
            <textarea>
        fun adderCallback(x: Int, y: Int, callback: JSObject) {
            val sum = x + y
            win(callback(sum))
        }
            </textarea>
            
            <button onclick="minimize()">Minimize Window</button>
            <textarea>
        _api.minimize();
            </textarea>
            <textarea>
        fun minimize() {
            stage.isIconified = true
        }
            </textarea>
            
            <button onclick="exit()">Exit</button>
            <textarea>
        _api.exit();
            </textarea>
            <textarea>
        fun exit() {
            Platform.exit()
        }
            </textarea>
        
        </div>
        <script>
            // _api is the api
        
            _api.log('hello from the frontend');
        
            console.log('test');
            console.log({"foo":"bar"});
            console.log(null);
            console.log(undefined);
            console.log(true);
            console.log(5);
            console.log(5.123153);
            console.log(function() { alert('test'); });
            console.log(Date);
            console.info('console.info');
            console.warn('console.warn');
            console.error('console.error');
        
            // convenience function
            function output(str) {
                document.getElementById('output').value = str;
            }
        
            output('Output from the examples below go here');
        
            function dirChooser() {
                _api.openDirChooser(dir => {
                    output(dir);
                });
            }
        
            function fileChooser() {
                _api.openFileChooser(dir => {
                    output(dir);
                });
            }
        
            function helloWorld() {
                output(_api.helloWorld());
            }
        
            function adder() {
                _api.adderCallback(1,2,sum => {
                    output(sum);
                })
            }
        
            function minimize() {
                    _api.minimize();
            }
        
            function exit() {
                    _api.exit();
            }
        
            document.getElementById('useragent').innerHTML = navigator.userAgent;
        </script>
        </body>
        </html>
    """.trimIndent()
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
                        println("Failed to initialize")
                        exitProcess(0)
                    }
                    else -> {}
                }
            }

            // load the entry page
            if (args.pageRelativePath.isNotEmpty()) engine.load(resource(args.pageRelativePath).toString())
            else {
                println("Loading test page")
                engine.loadContent(testPage)
                println("done")
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
            fun log(arg: Any?) = println("$green=>$clear " + parseConsole(arg))
            fun info(arg: Any?) = println("$blue?>$clear " + parseConsole(arg))
            fun warn(arg: Any?) = println("$yellowBG!$clear$yellow>$clear " + parseConsole(arg))
            fun error(arg: Any?) = println("${redBG}x$clear$red>$clear " + parseConsole(arg))
        }
        window.setMember("console", JSConsole())
    }

    /**
     * Gets a resource relative from the .jar/.exe
     */
    private fun resource(relativePath: String): URL {
        return {}.javaClass.getResource(relativePath)
    }

    /**
     * Gets a resource relative from the .jar/.exe as a stream
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