import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import netscape.javascript.JSObject
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import kotlin.system.exitProcess

class JFX: Application() {
    override fun start(primaryStage: Stage) {
        primaryStage.apply {
            title = "DeJaView test"
            width = 1200.0
            height = 800.0
            centerOnScreen()

            // Lock window size with: maxWidth, minHeight, etc
            // Make the window transparent with: initStyle(StageStyle.TRANSPARENT)
        }

        val args = DJVArgs(
                pageRelativePath = "example.html", // optional, defaults to a test page
                iconRelativePath = "", // optional, use a 64x64 png if possible
                apiVarName = "_api", // optional, defaults to _api
                disableRightClick = true, // optional, defaults to true - right clicking doesn't really do much
        )

        DeJaView(primaryStage, args, API())
    }
    override fun stop() {
        exitProcess(0)
    }
}

/*
    The API for the backend (JVM) to talk to the frontend (javascript)
    Everything must be a function here
    You can use `Platform.runLater{ ... }` to return to running code on the JavaFX thread if you're in a different thread
    `win` is a reference to Window: JSObject
    `stage` is a reference to primaryStage: Stage
 */
class API: DJVApi() {
    fun adderCallback(x: Int, y: Int, callback: JSObject) {
        val sum = x + y
        win(callback(sum))
    }

    fun helloWorld(): String {
        return "Hello World"
    }

    fun openDirChooser(callback: JSObject) {
        val dirChooser = DirectoryChooser()
        val selectedDir = dirChooser.showDialog(stage)
        win(callback(selectedDir.absolutePath))
    }

    fun openFileChooser(callback: JSObject) {
        val fileChooser = FileChooser()
        val selectedFile = fileChooser.showOpenDialog(stage) // or showSaveDialog
        win(callback(selectedFile.absolutePath))
    }

    fun log(str: String) {
        println(str)
    }

    fun minimize() {
        stage.isIconified = true
    }

    fun exit() {
        Platform.exit()
    }
}

// boilerplate to launch the JavaFX application and thread
fun main(args: Array<String>) {
    Application.launch(JFX::class.java)
}
