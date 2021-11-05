package org.jetbrains.plugins.scala
package lang.refactoring.rename.inplace

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.PsiUtilBase.getElementAtCaret
import com.intellij.psi.{PsiElement, PsiFile, PsiNamedElement}
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.{PsiElementRenameHandler, RenamePsiElementProcessor}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, invokeLaterInTransaction}
import org.jetbrains.plugins.scala.lang.psi.IndirectPsiReference.IntermediateTarget
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScBegin
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.light.{PsiClassWrapper, PsiMethodWrapper}
import org.jetbrains.plugins.scala.lang.refactoring.rename.ScalaRenameUtil
import org.jetbrains.plugins.scala.util.JListCompatibility

import scala.annotation.nowarn

/**
 * Nikolay.Tropin
 * 1/20/14
 */
trait ScalaInplaceRenameHandler {

  def isAvailable(element: PsiElement, editor: Editor, file: PsiFile): Boolean

  def renameProcessor(element: PsiElement): RenamePsiElementProcessor = {
    val isScalaElement = element match {
      case null => false
      case _: PsiMethodWrapper[_] | _: PsiClassWrapper => true
      case _ => element.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
    }
    val processor = if (isScalaElement) RenamePsiElementProcessor.forElement(element) else null
    if (processor != RenamePsiElementProcessor.DEFAULT) processor else null
  }

  def isLocal(element: PsiElement): Boolean =
    element.asOptionOf[PsiNamedElement] match {
      case Some(inNameContext(m: ScMember)) => m.isLocal || m.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)
      case _ => false
    }

  protected def doDialogRename(element: PsiElement, project: Project, nameSuggestionContext: PsiElement, editor: Editor): Unit = {
    PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor)
  }

  def afterElementSubstitution(elementToRename: PsiElement, editor: Editor, dataContext: DataContext)(inplaceRename: PsiElement => InplaceRefactoring): InplaceRefactoring = {
    def showSubstitutePopup(@Nls title: String, positive: String, subst: => PsiNamedElement): Unit = {
      val cancel = ScalaBundle.message("rename.cancel")
      val list = JListCompatibility.createJBListFromListData(positive, cancel)
      val callback: Runnable = () => invokeLaterInTransaction(editor.getProject) {
        list.getSelectedValue match {
          case s: String if s == positive =>
            val file = subst.getContainingFile.getVirtualFile
            if (FileDocumentManager.getInstance.getDocument(file) == editor.getDocument) {
              editor.getCaretModel.moveToOffset(subst.getTextOffset)
              inplaceRename(subst)
            } else {
              doDialogRename(subst, editor.getProject, null, editor)
            }
          case s: String if s == cancel =>
        }
      }
      JBPopupFactory.getInstance.createListPopupBuilder(list)
        .setTitle(title)
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(callback)
        .createPopup.showInBestPositionFor(editor): @nowarn("cat=deprecation")
    }

    def specialMethodPopup(fun: ScFunction): Unit = {
      val clazz = fun.containingClass
      val clazzType = clazz match {
        case _: ScObject => "object"
        case _: ScClass => "class"
        case _: ScTrait => "trait"
        case _: ScNewTemplateDefinition => "instance"
      }
      val title = ScalaBundle.message("rename.special.method.title")
      val positive = ScalaBundle.message("rename.special.method.rename.class", clazzType)
      showSubstitutePopup(title, positive, ScalaRenameUtil.findSubstituteElement(elementToRename))
    }
    def aliasedElementPopup(ref: ScReference): Unit = {
      val title = ScalaBundle.message("rename.aliased.title")
      val positive = ScalaBundle.message("rename.aliased.rename.actual")
      showSubstitutePopup(title, positive, ScalaRenameUtil.findSubstituteElement(elementToRename))
    }

    val selected = getElementAtCaret(editor)
      .nonStrictParentOfType(Seq(classOf[ScReference], classOf[ScNamedElement]))
    val nameId = selected.collect {
      case ref: ScReference => ref.nameId
      case named: ScNamedElement => named.nameId
    }.orNull

    val actualElementToRename = elementToRename match {
      case IntermediateTarget(finalTarget: ScBegin) => finalTarget.namedElement match {
        case Some(element) =>
          editor.getCaretModel.moveToOffset(element.getTextOffset)
          element
        case None => finalTarget
      }
      case e => e
    }

    actualElementToRename match {
      case fun: ScFunction if selected.contains(fun) && isSpecial(fun) =>
        specialMethodPopup(fun)
        null
      case e =>
        if (nameId != null) nameId.getParent match {
          case ref: ScReference if ScalaRenameUtil.isAliased(ref) =>
            aliasedElementPopup(ref)
            return null
          case _ =>
        }
        inplaceRename(ScalaRenameUtil.findSubstituteElement(e))
    }
  }

  private def isSpecial(f: ScFunction): Boolean = {
    val hasSpecialName = f.name match {
      case "apply" | "unapply" | "unapplySeq" => true
      case _ => false
    }
    hasSpecialName || f.isConstructor
  }
}
