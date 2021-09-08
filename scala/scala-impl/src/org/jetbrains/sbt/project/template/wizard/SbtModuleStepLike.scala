package org.jetbrains.sbt.project.template.wizard

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.{JBCheckBox, JBTextField}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.Versions
import org.jetbrains.plugins.scala.project.template.ScalaVersionDownloadingDialog
import org.jetbrains.sbt.SbtBundle
import org.jetbrains.sbt.project.template.SComboBox

/**
 * The trait contains common logic which is reused in two different "New Project Wizard" APIs.
 *
 * Currently (2021.3) there are two APIs:
 *  1. "OLD", where new project step is created in<br>
 *     [[org.jetbrains.sbt.project.template.SbtModuleBuilder.modifySettingsStep]]
 *  1. "NEW", where new project step is created in <br>
 *     [[org.jetbrains.sbt.project.template.wizard.ScalaNewProjectWizard.createStep]]
 */
//TODO: hide "package prefix" in "Advanced settings"
//TODO: add "artifact coordinates" settings like in Maven or Gradle
private[template] trait SbtModuleStepLike {

  protected def selections: SbtModuleStepSelections

  //
  // Scala & Sbt versions, initialized lazily from the Internet
  // TODO: improve this in SCL-19189
  //
  protected val availableScalaVersions: Versions
  protected val availableSbtVersions: Versions
  protected val availableSbtVersionsForScala3: Versions

  //
  // Raw UI elements
  //
  protected final val sbtVersionComboBox: SComboBox[String] = new SComboBox[String]
  protected final val scalaVersionComboBox: SComboBox[String] = new SComboBox[String]

  protected final val downloadSbtSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.sbt.sources"))
  )
  protected final val downloadScalaSourcesCheckbox: JBCheckBox = applyTo(new JBCheckBox(SbtBundle.message("sbt.module.step.download.sources")))(
    _.setToolTipText(SbtBundle.message("sbt.download.scala.standard.library.sources"))
  )

  protected final val packagePrefixField: JBTextField = applyTo(new JBTextField())(
    _.getEmptyText.setText(ScalaBundle.message("package.prefix.example"))
  )

  /**
   * Initializes selections and UI elements only once
   */
  protected final def initSelectionsAndUi(): Unit = {
    _initSelectionsAndUi
  }
  private lazy val _initSelectionsAndUi: Unit = {
    selections.update(Versions.SBT, availableSbtVersions)
    selections.update(Versions.Scala, availableScalaVersions)

    initUiElementsModel()
    initUiElementsListeners()
  }

  private def initUiElementsModel(): Unit = {
    sbtVersionComboBox.setItems(availableSbtVersions.versions.toArray)
    scalaVersionComboBox.setItems(availableScalaVersions.versions.toArray)

    initUiElementsModelFrom(selections)
    initSelectedScalaVersion()
    updateSupportedSbtVersionsForSelectedScalaVersion()
  }

  private def initUiElementsModelFrom(selections: SbtModuleStepSelections): Unit = {
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    scalaVersionComboBox.setSelectedItemSafe(selections.scalaVersion.orNull)
    downloadSbtSourcesCheckbox.setSelected(selections.resolveSbtClassifiers)
    downloadScalaSourcesCheckbox.setSelected(selections.resolveClassifiers)
    packagePrefixField.setText(selections.packagePrefix.getOrElse(""))
  }

  /**
   * Init UI --> Selections binding
   */
  private def initUiElementsListeners(): Unit = {
    sbtVersionComboBox.addActionListener { _ =>
      selections.sbtVersion = sbtVersionComboBox.getSelectedItemTyped
    }
    scalaVersionComboBox.addActionListener { _ =>
      selections.scalaVersion = scalaVersionComboBox.getSelectedItemTyped

      updateSupportedSbtVersionsForSelectedScalaVersion()
    }

    downloadScalaSourcesCheckbox.addChangeListener(_ =>
      selections.resolveClassifiers = downloadScalaSourcesCheckbox.isSelected
    )
    downloadSbtSourcesCheckbox.addChangeListener { _ =>
      selections.resolveSbtClassifiers = downloadSbtSourcesCheckbox.isSelected
    }

    packagePrefixField.getDocument.addDocumentListener(
      (_ => selections.packagePrefix = Option(packagePrefixField.getText).filter(_.nonEmpty)): DocumentAdapter
    )
  }

  private def isScala3Version(scalaVersion: String): Boolean =
    scalaVersion.startsWith("3")

  private def initSelectedScalaVersion(): Unit = {
    selections.scalaVersion match {
      case Some(version) if availableScalaVersions.versions.contains(version) =>
        scalaVersionComboBox.setSelectedItemSafe(version)

        if (selections.scrollScalaVersionDropdownToTheTop) {
          ScalaVersionDownloadingDialog.UiUtils.scrollToTheTop(scalaVersionComboBox)
        }
      case _ if scalaVersionComboBox.getItemCount > 0 =>
        scalaVersionComboBox.setSelectedIndex(0)
      case _ =>
    }
  }

  /**
   * Ensure that we do not show sbt versions < 1.5 if Scala 3.X is selected
   */
  private def updateSupportedSbtVersionsForSelectedScalaVersion(): Unit = {
    val isScala3Selected = selections.scalaVersion.exists(isScala3Version)
    val supportedSbtVersions = if (isScala3Selected) availableSbtVersionsForScala3 else availableSbtVersions
    sbtVersionComboBox.setItems(supportedSbtVersions.versions.toArray)

    // if we select Scala3 version but had Scala2 version selected before and some sbt version incompatible with Scala3,
    // the latest item from the list will be automatically selected
    sbtVersionComboBox.setSelectedItemSafe(selections.sbtVersion.orNull)
    selections.update(Versions.SBT, availableSbtVersions)
  }
}