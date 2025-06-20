package cn.cdtft.plugin.aep.kt

import cn.cdtft.plugin.aep.PsiUtils
import cn.cdtft.plugin.aep.ShowUsagesAction
import cn.cdtft.plugin.aep.utils.Constants
import cn.cdtft.plugin.aep.utils.MLog
import cn.cdtft.plugin.aep.ext.isKotlin
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.find.FindManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.stubs.impl.KotlinUserTypeStubImpl

class LineMarkerProviderKotlin : LineMarkerProvider {
    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (!PsiUtils.checkIsKotlinInstalled()) return null
        if (!psiElement.isKotlin()) return null

        if (PsiUtils.isEventBusPost(psiElement)) {
            return LineMarkerInfo(
                psiElement, psiElement.textRange, Constants.ICON,
                null, SHOW_RECEIVERS, GutterIconRenderer.Alignment.LEFT
            )
        } else if (PsiUtils.isEventBusReceiver(psiElement)) {
            return LineMarkerInfo(
                psiElement, psiElement.textRange, Constants.ICON,
                null, SHOW_SENDERS, GutterIconRenderer.Alignment.LEFT
            )
        }

        return null
    }


    override fun collectSlowLineMarkers(
        list: MutableList<out PsiElement?>,
        collection: MutableCollection<in LineMarkerInfo<*>?>
    ) {
    }

}

private val SHOW_SENDERS: GutterIconNavigationHandler<PsiElement> =
    GutterIconNavigationHandler<PsiElement> { e, psiElement ->
        MLog.debug("kt SHOW_SENDERS 0: " + psiElement.text)
        if (psiElement.isKotlin() && psiElement is KtNamedFunction) {
            val project = psiElement.project
            val psiFacade = JavaPsiFacade.getInstance(project)
            val module = ModuleUtilCore.findModuleForPsiElement(psiElement)
            val eventBusClass = psiFacade.findClass(
                Constants.FUN_EVENT_CLASS, GlobalSearchScope.moduleWithLibrariesScope(
                    module!!
                )
            )
            var postMethod: PsiMethod? = null
            if (eventBusClass != null) {
                postMethod = eventBusClass.findMethodsByName(Constants.FUN_NAME, false)[0]
                MLog.debug("kt SHOW_SENDERS 1: " + eventBusClass.text.substring(0, 25))
                MLog.debug("kt SHOW_SENDERS 2: " + postMethod.text.substring(0, 20))
            }
            //JavaCodeContextType.Declaration dd;
            //org.jetbrains.kotlin.psi.KtUserType ktUserType;
            //org.jetbrains.kotlin.psi.KtNameReferenceExpression ktNameReferenceExpression;
            //com.intellij.psi.impl.source.tree.LeafPsiElement leafPsiElement;
            var eventClass: LeafPsiElement? = null
            var parameter: KtParameter? = null
            var typeReference: KtTypeReference? = null
            var ktUserType: KtUserType? = null
            var ktNameReferenceExpression: KtNameReferenceExpression? = null
            val userTypeStub: KotlinUserTypeStubImpl? = null
            val function = psiElement
            val parameterList = function.valueParameterList
            if (parameterList != null && parameterList.parameters.size == 1) {
                parameter = parameterList.parameters[0]
                typeReference = parameter.typeReference
                ktUserType = typeReference!!.firstChild as KtUserType?
                //userTypeStub = new KotlinUserTypeStubImpl(ktUserType.getStub());
                ktNameReferenceExpression = ktUserType!!.firstChild as KtNameReferenceExpression?
                eventClass = ktNameReferenceExpression!!.firstChild as LeafPsiElement?
                MLog.debug("kt SHOW_SENDERS 3: $eventClass")
            }

            if (postMethod != null && eventClass != null) {
                val project2 = postMethod.project
                val findUsagesManager =
                    (FindManager.getInstance(project) as FindManagerImpl).findUsagesManager


                //new KotlinFindUsagesHandlerFactory(project).getFindClassOptions();
                //KotlinFindUsagesHandlerFactory kotlinFindUsagesHandlerFactory = new KotlinFindUsagesHandlerFactory(project);

                //                            FindUsagesHandler findUsagesHandler = kotlinFindUsagesHandlerFactory.createFindUsagesHandler(parameter, false);
                //                            AbstractFindUsagesDialog dialog2 = findUsagesHandler.getFindUsagesDialog(false, true, true);
                //                            dialog2.showAndGet();
                //dialog2.show();
                //
                //                            KtClass ktClass = new KtClass(postMethod.getNode());
                //                            KotlinFindUsagesHandler dd = new KotlinTypeParameterFindUsagesHandler(ktClass, kotlinFindUsagesHandlerFactory);
                //                            //KotlinFindClassUsagesHandler handlers = new KotlinFindClassUsagesHandler(ktClass, kotlinFindUsagesHandlerFactory);
                //KotlinFindClassUsagesDialog dialog = (KotlinFindClassUsagesDialog) handlers.getFindUsagesDialog(false, true, false); */
                //                            AbstractFindUsagesDialog dialog = dd.getFindUsagesDialog(false, true, false);
                //                            dialog.show();
                //
                //                            Collection psiReferences = dd.findReferencesToHighlight(parameter, GlobalSearchScope.allScope(project));
                //                            for (Object p:psiReferences){
                //                                PsiReference p1= (PsiReference) p;
                //                                MLog.debug(p1.toString());
                //                            }

                //
                //                            StubBasedPsiElementBase d;


                //KotlinFindUsagesProvider findUsagesProvider = new KotlinFindUsagesProvider();

                //findUsagesProvider.getWordsScanner();

                //DefaultWordsScanner defaultWordsScanner;
                //FileEditor editor = PsiUtilBase.findEditor(psiElement);
                //KotlinEditorOptions options;
                //findUsagesManager.findUsages(eventClass, null, );
                ShowUsagesAction(SenderFilterKotlin(eventClass))
                    .startFindUsages(
                        postMethod,
                        RelativePoint(e),
                        PsiEditorUtil.findEditor(eventClass),
                        Constants.MAX_USAGES
                    )
            }
        }
    }

private val SHOW_RECEIVERS: GutterIconNavigationHandler<PsiElement> =
    GutterIconNavigationHandler<PsiElement> { e, psiElement ->
        MLog.debug("kt SHOW_RECEIVERS 0: " + psiElement.text)


        if (psiElement is KtDotQualifiedExpression) {
            MLog.debug("kt SHOW_RECEIVERS 1: " + psiElement.text)

            try {
                val expression = psiElement
                val callExpression = expression.lastChild as KtCallExpression
                val argumentList = callExpression.valueArgumentList
                val argument = argumentList!!.arguments[0]
                val referenceExpression = argument.firstChild as KtNameReferenceExpression
                val leafPsiElement = referenceExpression.firstChild as LeafPsiElement

                //KtPsiClassWrapper psiClassWrapper= KotlinJavaPsiFacade.getInstance(psiElement.getProject()).findClass(leafPsiElement.getClass(),)
                MLog.debug("kt SHOW_RECEIVERS 2: " + argument.text)
                val ktClass = KtClass(leafPsiElement.node)
                MLog.debug("kt SHOW_RECEIVERS 3: " + ktClass.text)
                ShowUsagesAction(ReceiverFilterKotlin())
                    .startFindUsages(
                        ktClass, RelativePoint(e),
                        PsiEditorUtil.findEditor(psiElement),
                        Constants.MAX_USAGES
                    )
            } catch (throwable: Exception) {
                throwable.fillInStackTrace()
            } catch (throwable: Error) {
                throwable.fillInStackTrace()
            }


            //                        PsiType[] expressionTypes = expression.getArgumentList().getExpressionTypes();
            //                        if (expressionTypes.length > 0) {
            //                            PsiClass eventClass = PsiUtils.getClass(expressionTypes[0]);
            //                            if (eventClass != null) {
            //                                new ShowUsagesAction(new ReceiverFilterKotlin())
            //                                        .startFindUsages(
            //                                                eventClass, new RelativePoint(e),
            //                                                PsiUtilBase.findEditor(psiElement),
            //                                                Constants.MAX_USAGES);
            //                            }
            //                        }
        }
    }