//     Project: sbt-electron
//      Module:
// Description:
package de.surfice.sbtelectron

import de.surfice.sbtnpm.NpmPlugin
import de.surfice.sbtnpm.utils
import sbt.{Def, _}
import Keys._
import de.surfice.sbtnpm.utils.FileWithLastrun
import org.scalajs.sbtplugin.{ScalaJSPlugin, ScalaJSPluginInternal, Stage}

object ElectronPlugin extends AutoPlugin {
  override def requires: Plugins = NpmPlugin

  object autoImport {
    val electronTargetDir: SettingKey[File] =
      settingKey[File]("target directory for electron")

    val electronVersion: SettingKey[String] =
      settingKey[String]("npm electron version string")

    val electronMainJsFile: SettingKey[File] =
      settingKey[File]("Path to the electron main.js file")

    val electronMainJs: TaskKey[String] =
      taskKey[String]("Content of the electron main.js file")

    val electronWriteMainJs: TaskKey[FileWithLastrun] =
      taskKey[FileWithLastrun]("Create the electron main.js file (scoped to fastOptJS or FullOptJS)")

    val electron: TaskKey[Long] =
      taskKey[Long]("runs the electron app (scoped to fastOptJS or fullOptJS)")
  }

  import autoImport._
  import ScalaJSPlugin.autoImport.{fastOptJS,fullOptJS}
  import NpmPlugin.autoImport.{npmWritePackageJson,npmMain,npmScripts,npmCmd,npmInstall}

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    npmMain := Some("main.js"),

    npmScripts ++= Seq(
      "fastopt" -> "node_modules/electron/cli.js . fastopt",
      "fullopt" -> "node_modules/electron/cli.js . fullopt"
    ),

    electronTargetDir := baseDirectory.value,

    electronVersion := "~1.6.11",

    electronMainJsFile := electronTargetDir.value / "main.js",

    electronMainJs := {
      val fastopt = (artifactPath in (Compile,fastOptJS)).value
      val fullopt = (artifactPath in (Compile,fullOptJS)).value

      s"""switch(process.argv[2]) {
         |  case "fastopt":
         |    require("$fastopt").Main();
         |    break;
         |  case "fullopt":
         |    require("$fullopt").Main();
         |    break;
         |};
         |  """.stripMargin
    },

    electronWriteMainJs := {
      npmWritePackageJson.value
      val file = electronMainJsFile.value
      val lastrun = electronWriteMainJs.previous
      if(lastrun.isEmpty || lastrun.get.needsUpdateComparedToConfig(baseDirectory.value)) {
        IO.write(file,electronMainJs.value)
        FileWithLastrun(file)
      }
      else
        lastrun.get
    }
  ) ++
    perScalaJSStageSettings(Stage.FullOpt) ++
    perScalaJSStageSettings(Stage.FastOpt)


  private def perScalaJSStageSettings(stage: Stage): Seq[Def.Setting[_]] = {
    val stageTask = ScalaJSPluginInternal.stageKeys(stage)

    Seq(
      defineElectronTask(stageTask)
    )
  }

  private def defineElectronTask(scope: Any) = scope match {
    case scoped: Scoped =>
      electron in scoped := {
         npmInstall.value
      }
  }


}
