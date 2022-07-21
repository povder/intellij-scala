package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.io.FileUtilRt.getNameWithoutExtension
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean
import org.jetbrains.plugins.scala.util.BaseIconProvider

import javax.swing.Icon

sealed trait FileKind {
  protected val delegate: ScTypeDefinition

  def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode]
}

object FileKind {
  import extensions._
  import icons.Icons._

  def unapply(file: ScalaFile): Option[FileKind] = {
    val fileName = clean(getNameWithoutExtension(file.name))

    def matchesFileName(definition: ScTypeDefinition): Boolean =
      clean(definition.name) == fileName

    def bothMatchFileName(first: ScTypeDefinition, second: ScTypeDefinition): Boolean =
      first.name == second.name && clean(first.name) == fileName

    val (typeDefinitions, others) = file.typeDefinitionsAndOthers
    if (others.nonEmpty)
      None
    else
      typeDefinitions.toList match {
        case (definition: ScObject)                  :: Nil if definition.isPackageObject       => Some(PackageObject(definition))
        case definition                              :: Nil if matchesFileName(definition)      => Some(TypeDefinition(definition))
        case (first: ScClass)  :: (second: ScObject) :: Nil if bothMatchFileName(first, second) => Some(ClassAndCompanionObject(first, second))
        case (first: ScObject) :: (second: ScClass)  :: Nil if bothMatchFileName(first, second) => Some(ClassAndCompanionObject(second, first))
        case (first: ScTrait)  :: (second: ScObject) :: Nil if bothMatchFileName(first, second) => Some(TraitAndCompanionObject(first, second))
        case (first: ScObject) :: (second: ScTrait)  :: Nil if bothMatchFileName(first, second) => Some(TraitAndCompanionObject(second, first))
        case _ => None
      }
  }

  private sealed trait SingleDefinition extends FileKind

  private case class PackageObject(override protected val delegate: ScObject) extends SingleDefinition {

    override def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode] =
      Some(new PackageObjectNode(delegate))
  }

  private case class TypeDefinition(override protected val delegate: ScTypeDefinition) extends SingleDefinition {
    override def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode] =
      Some(new TypeDefinitionNode(delegate))
  }

  private sealed trait PairedDefinition extends FileKind with Iconable {
    protected val companionObject: ScObject

    override def node(implicit project: Project, settings: ViewSettings): Option[Node with IconableNode] =
      if (settings != null && settings.isShowMembers) {
        None
      } else {
        final class LeafNode extends PsiFileNode(project, delegate.getContainingFile, settings) with IconableNode {

          override def getIcon(flags: Int): Icon = PairedDefinition.this.getIcon(flags)

          override def isAlwaysLeaf: Boolean = true

          //noinspection TypeAnnotation
          override def getChildrenImpl = emptyNodesList

          override def updateImpl(data: PresentationData): Unit = {
            super.updateImpl(data)
            setIcon(data)
            data.setPresentableText(delegate.name)
          }
        }

        Some(new LeafNode)
      }
  }

  private final case class ClassAndCompanionObject(override protected val delegate: ScClass,
                                                   override protected val companionObject: ScObject)
    extends PairedDefinition with BaseIconProvider {
    protected override val baseIcon: Icon =
      if (delegate.hasAbstractModifier) ABSTRACT_CLASS_AND_OBJECT else CLASS_AND_OBJECT
  }

  private final case class TraitAndCompanionObject(override protected val delegate: ScTrait,
                                                   override protected val companionObject: ScObject)
    extends PairedDefinition with BaseIconProvider {
    protected override val baseIcon: Icon = TRAIT_AND_OBJECT
  }
}