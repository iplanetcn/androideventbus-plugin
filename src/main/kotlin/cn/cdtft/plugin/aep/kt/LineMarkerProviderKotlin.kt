package cn.cdtft.plugin.aep.kt

import cn.cdtft.plugin.aep.PsiUtils
import cn.cdtft.plugin.aep.ShowUsagesAction
import cn.cdtft.plugin.aep.utils.Constants
import cn.cdtft.plugin.aep.utils.MLog
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
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.stubs.impl.KotlinUserTypeStubImpl
import java.awt.event.MouseEvent

/**
 * Created by by likfe ( https://github.com/likfe/ )  on 18/03/05.
 */
class LineMarkerProviderKotlin : LineMarkerProvider {
    override fun getLineMarkerInfo(psiElement: PsiElement): LineMarkerInfo<*>? {
        if (!PsiUtils.checkIsKotlinInstalled()) return null
        if (!PsiUtils.isKotlin(psiElement)) return null

        if (PsiUtils.isEventBusPost(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement, psiElement.getTextRange(), Constants.ICON,
                null, SHOW_RECEIVERS, GutterIconRenderer.Alignment.LEFT
            )
        } else if (PsiUtils.isEventBusReceiver(psiElement)) {
            return LineMarkerInfo<PsiElement?>(
                psiElement, psiElement.getTextRange(), Constants.ICON,
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

    companion object {
        private val SHOW_SENDERS: GutterIconNavigationHandler<PsiElement> =
            object : GutterIconNavigationHandler<PsiElement> {
                override fun navigate(e: MouseEvent, psiElement: PsiElement) {
                    MLog.debug("kt SHOW_SENDERS 0: " + psiElement.getText())
                    if (PsiUtils.isKotlin(psiElement) && psiElement is KtNamedFunction) {
                        val project = psiElement.getProject()
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
                            MLog.debug("kt SHOW_SENDERS 1: " + eventBusClass.getText().substring(0, 25))
                            MLog.debug("kt SHOW_SENDERS 2: " + postMethod.getText().substring(0, 20))
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
                        val parameterList = function.getValueParameterList()
                        if (parameterList != null && parameterList.getParameters().size == 1) {
                            parameter = parameterList.getParameters().get(0)
                            typeReference = parameter.getTypeReference()
                            ktUserType = typeReference!!.getFirstChild() as KtUserType?
                            //userTypeStub = new KotlinUserTypeStubImpl(ktUserType.getStub());
                            ktNameReferenceExpression = ktUserType!!.getFirstChild() as KtNameReferenceExpression?
                            eventClass = ktNameReferenceExpression!!.getFirstChild() as LeafPsiElement?
                            MLog.debug("kt SHOW_SENDERS 3: " + eventClass.toString())
                        }

                        if (postMethod != null && eventClass != null) {
                            val project2 = postMethod.getProject()
                            val findUsagesManager =
                                (FindManager.getInstance(project) as FindManagerImpl).getFindUsagesManager()


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
                                    PsiUtilBase.findEditor(eventClass),
                                    Constants.MAX_USAGES
                                )
                        }
                    }
                }
            }

        private val SHOW_RECEIVERS: GutterIconNavigationHandler<PsiElement> =
            object : GutterIconNavigationHandler<PsiElement> {
                override fun navigate(e: MouseEvent, psiElement: PsiElement) {
                    MLog.debug("kt SHOW_RECEIVERS 0: " + psiElement.getText())


                    if (psiElement is KtDotQualifiedExpression) {
                        MLog.debug("kt SHOW_RECEIVERS 1: " + psiElement.getText())

                        try {
                            val expression = psiElement
                            val callExpression = expression.getLastChild() as KtCallExpression
                            val argumentList = callExpression.getValueArgumentList()
                            val argument = argumentList!!.getArguments().get(0)
                            val referenceExpression = argument.getFirstChild() as KtNameReferenceExpression
                            val leafPsiElement = referenceExpression.getFirstChild() as LeafPsiElement

                            //KtPsiClassWrapper psiClassWrapper= KotlinJavaPsiFacade.getInstance(psiElement.getProject()).findClass(leafPsiElement.getClass(),)
                            MLog.debug("kt SHOW_RECEIVERS 2: " + argument.getText())
                            val ktClass = KtClass(leafPsiElement.getNode())
                            MLog.debug("kt SHOW_RECEIVERS 3: " + ktClass.getText())
                            ShowUsagesAction(ReceiverFilterKotlin())
                                .startFindUsages(
                                    ktClass, RelativePoint(e),
                                    PsiUtilBase.findEditor(psiElement),
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
            }
    }
}
