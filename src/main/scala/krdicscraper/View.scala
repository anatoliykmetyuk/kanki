package krdicscraper

import scala.language.postfixOps

import scala.util.Try
import API.tryToError

import java.io.File

import scala.swing._

import scala.swing._
import scala.swing.BorderPanel.Position._
import event._
import java.awt.Point

import akka.actor._
import scala.concurrent.duration._

import Protocol._

object View extends SimpleSwingApplication {
  val wlLbl = new Label("Word list")
  val wlFld = new TextField { editable = false }
  val wlBtn = new Button("Choose")

  val ofLbl = new Label("Output directory")
  val ofFld = new TextField { editable = false }
  val ofBtn = new Button("Choose")

  val thLbl = new Label("Throttle (seconds)")
  val thFld = new TextField
  val thBtn = new Button("Set")

  val log = new TextArea { editable = false }

  val startBtn    = new Button("Start" )
  val cancelBtn   = new Button("Cancel")
  val progressBar = new ProgressBar

  def writeLog(str: String): Unit = Swing.onEDT { log.text += str + '\n' }

  def setProgress(value: Int, max: Int): Unit = Swing.onEDT {
    progressBar.value = value
    progressBar.max   = max
  }

  def incremetProgress(): Unit = Swing.onEDT { progressBar.value += 1 }

  def updateGui(): Unit = {
    startBtn .enabled = thFld.text.nonEmpty && outputDir.nonEmpty && wordList.nonEmpty && workerActor.isEmpty
    cancelBtn.enabled = workerActor.nonEmpty
  }

  // State
  var _wordList: Option[File] = None
  def wordList = _wordList
  def wordList_=(f: Option[File]) = {
    _wordList = f
    Swing.onEDT { wlFld.text = f.map(_.getAbsolutePath).getOrElse("") }
    for { file <- f } writeLog(s"Word list file set: ${file.getAbsolutePath}")
  }

  var _outputDir: Option[File] = None
  def outputDir = _outputDir
  def outputDir_=(f: Option[File]) = {
    _outputDir = f
    Swing.onEDT { ofFld.text = f.map(_.getAbsolutePath).getOrElse("") }
    for { file <- f } writeLog(s"Output directory set: ${file.getAbsolutePath}")
  }

  val system = ActorSystem("PiSystem")
  var workerActor: Option[ActorRef] = None


  def top = new MainFrame { // top is a required method
    title = "KrDic Scraper"
    location = new Point(400, 250)

    val gridPanel = new GridPanel(3, 3) {
      contents ++= List(
        wlLbl, wlFld, wlBtn
      , ofLbl, ofFld, ofBtn
      , thLbl, thFld, thBtn
      )
    }

    val controlPanel = new GridPanel(1, 2) {
      contents ++= List(startBtn, cancelBtn)
    }

    val logPane = new ScrollPane(log) {
      horizontalScrollBarPolicy = ScrollPane.BarPolicy.AsNeeded
      verticalScrollBarPolicy   = ScrollPane.BarPolicy.Always
    }

    val combined = new BorderPanel {
      layout(gridPanel   ) = North
      layout(logPane     ) = Center
      layout(controlPanel) = South
    }

    contents = new BorderPanel {
      layout(combined   ) = Center
      layout(progressBar) = South
    }
    size = new Dimension(400, 300)

    List(ofBtn, wlBtn, startBtn, cancelBtn, thBtn).foreach(btn => listenTo(btn))

    setProgress(0, 100)

    reactions += {
      case ButtonClicked(`wlBtn`) =>
        val chooser = new FileChooser()
        chooser.title = "Choose the word list file"
        val result = chooser.showOpenDialog(null)
        if (result == FileChooser.Result.Approve) wordList = Some(chooser.selectedFile)
        updateGui()

      case ButtonClicked(`ofBtn`) =>
        val chooser = new FileChooser()
        chooser.title = "Choose the output directory"
        chooser.fileSelectionMode = FileChooser.SelectionMode.DirectoriesOnly
        val result = chooser.showOpenDialog(null)
        if (result == FileChooser.Result.Approve) outputDir = Some(chooser.selectedFile)
        updateGui()

      case ButtonClicked(`startBtn`) =>
        (for {
          f        <- tryToError(Try { wordList .get })
          outDir   <- tryToError(Try { outputDir.get })
          words    <- API.parseWordList(f)
          throttle <- Try { thFld.text.toInt }
        } yield {
          workerActor = Some(system actorOf WorkerActor.props(words, outDir))
          workerActor.foreach { _ ! Start(throttle) }
        }) match {
          case Right(_) => writeLog("Scraping started")
          case Left(err) => writeLog(err)
        }

        updateGui()

      case ButtonClicked(`cancelBtn`) =>
        workerActor.foreach(_ ! Stop)
        workerActor = None

        writeLog("Scraping canceled")
        updateGui()

      case ButtonClicked(`thBtn`) =>
        (for {
          t <- tryToError(Try(thFld.text.toInt))
        } yield {
          workerActor.foreach(_ ! UpdateThrottle(t))
          t
        }) match {
          case Right(t)  => writeLog(s"Throttle updated to $t")
          case Left(err) => writeLog(err)
        }
        updateGui()
    }
  }

  updateGui()
}

class WorkerActor(words: List[String], outputDir: File) extends Actor {
  import context.dispatcher

  var throttle  = 1
  var wordsLeft = words
  var progress  = 0
  val dicFile   = new File(outputDir, "dictionary.csv")
  val mp3Dir    = new File(outputDir, "pronunciations")
  val size      = words.size

  def scheduleContinuation = 
    context.system.scheduler.scheduleOnce(throttle seconds, self, Continue)

  def log(msg: String) = View.writeLog(msg)
  def sendProgress() = View.setProgress(progress, size)

  override def receive = dormant

  val dormant: Actor.Receive = {
    case Start(t) =>
      throttle = t
      if (!dicFile.exists) API.appendToFile(dicFile, "Word,Picture,Pronunciation,Actual Pronunciation,Explanation,Personal Connection,Part of Speech,Chinese,Chinese_Dic")
      scheduleContinuation
      context become working
  }

  def recover(err: API.Error[AnyRef]): API.Error[AnyRef] = err match {
    case e @ Right(_) => e
    case Left(err) => log(err); Right(null)
  }

  val working: Actor.Receive = {
    case Continue => wordsLeft match {
      case x :: xs =>
        log(s"$x: scraping started")
        val res = for {
          entry <- API.grab(x)
          _     <- recover(API.downloadPronunciation(entry, mp3Dir))
          _     <- API.appendToFile(dicFile, entry.toCSV(","))
        } yield ()
        res match {
          case Right(_  ) => log(s"$x: scraping finished. ${xs.size} words left.")
          case Left (err) => log(err)
        }

        wordsLeft  = xs
        progress  += 1

        sendProgress()
        scheduleContinuation

      case Nil =>
        log("All files were processed.")
        View.workerActor = None
        View.updateGui()
        context stop self
    }

    case Stop =>
      log("Operation aborted.")
      View.workerActor = None
      View.updateGui()
      context stop self

    case UpdateThrottle(t) =>
      throttle = t
      log(s"Throttle set to $t seconds.")
  }
}
object WorkerActor {
  def props(words: List[String], outDir: File) = Props(classOf[WorkerActor], words, outDir)
}

object Protocol {
  sealed trait Message
  case class Start(throttle: Int) extends Message
  case object Stop extends Message
  case class UpdateThrottle(throttle: Int) extends Message
  case object Continue extends Message
}
