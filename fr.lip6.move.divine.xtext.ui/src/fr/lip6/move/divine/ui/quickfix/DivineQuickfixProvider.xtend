/*
* generated by Xtext
*/
package fr.lip6.move.divine.ui.quickfix

import fr.lip6.move.divine.divine.Array
import fr.lip6.move.divine.divine.Channel
import fr.lip6.move.divine.divine.Constant
import fr.lip6.move.divine.divine.Variable
import fr.lip6.move.divine.validation.DivineValidator
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.ui.editor.model.edit.IModificationContext
import org.eclipse.xtext.ui.editor.quickfix.DefaultQuickfixProvider
import org.eclipse.xtext.ui.editor.quickfix.Fix
import org.eclipse.xtext.ui.editor.quickfix.IssueResolutionAcceptor
import org.eclipse.xtext.validation.Issue
import org.eclipse.emf.common.util.EList
import java.beans.Expression
import fr.lip6.move.divine.divine.NumberLiteral

//import org.eclipse.xtext.ui.editor.quickfix.Fix
//import org.eclipse.xtext.ui.editor.quickfix.IssueResolutionAcceptor
//import org.eclipse.xtext.validation.Issue
/**
 * Custom quickfixes.
 *
 * see http://www.eclipse.org/Xtext/documentation.html#quickfixes
 */
class DivineQuickfixProvider extends DefaultQuickfixProvider {

	/**
 * this method suggest to rename the variable , constant or array if their name is duplicated 
 *change Var name from X to X_1
 * 
 * 
 */
	@Fix(DivineValidator.DUPLICATED_NAME)
	def ChangeVariableName(Issue issue, IssueResolutionAcceptor acceptor) {

		acceptor.accept(issue, "Change the Qualifier name ", "Choose other name , this will rename the element into element_1", null,
			([ EObject element, IModificationContext context |
				var SEP = "_1";
				if (element instanceof Variable) {
					var Variable v = element as Variable;
					v.setName(v.name + SEP);
				} else if (element instanceof Array) {
					var Array array = element as Array;
					array.setName(array.getName() + SEP);
				} else if (element instanceof Channel) {
					var Channel ch = element as Channel;
					ch.setName(ch.getName() + SEP);
				}

				else if (element instanceof Constant) {
					var Constant t = element as Constant;
					t.setName(t.getName() + SEP);
				} else {
					System.err.println("Not yet implemented");
					System.out.println("error source : " + element.getClass().getName());
				}
			]));
	}

	/**
	 * Adapt the array size to match the array declared content size
	 * 
	 * array size > arraycontent size
	 */
	@Fix(DivineValidator.INVALID_ARRAY_SIZE_EXEED_CONTENT)
	def adapt_ArraySize_ToContent(Issue issue, IssueResolutionAcceptor acceptor) {
		acceptor.accept(issue, "Change array size to match content size ", "Change array size to match the array content size at initialization", null,
			([ EObject element, IModificationContext context |
				if (element instanceof Array) {
					var Array array = element as Array;

					if (array.initValue == null) {
						array.setSize(0);
					} else {
						array.setSize(array.initValue.size());
					}
				} else {
					System.err.println("Not yet implemented");
					System.out.println("Class source error :" + element.getClass().getName());
				}
			]));
	}
	
	/**
	 * array size > array content size 
	 * adapt the content  size to it declared array size by adding elements
	 * 
	 */

	@Fix(DivineValidator.INVALID_ARRAY_SIZE_EXEED_CONTENT)
	def adapt_arrayContent_ToSize_(Issue issue, IssueResolutionAcceptor acceptor) {
		acceptor.accept(issue, "add element to initial array values ", "Change array Content to match the array size declaration", null,
			([ EObject element, IModificationContext context |
			     
				if(element instanceof Array)
				{
				   var  NumberLiteral num ;	
				   num.setValue(0);
				   var fr.lip6.move.divine.divine.Expression expr =num;
                   var	Array arr =  element as Array ; 
                   var nbElementsToAdd =  arr.size -   arr.initValue.size; 
                   
				for(i :0 ..nbElementsToAdd-1)
					{
						
						arr.initValue.add(expr);
						
					}
					}
					 else {
					System.err.println("Not yet implemented");
					System.out.println("class error : " + element.getClass().getName());
				}
			
			]));

	}

	/**
	 * 
	 * array content size > array size
 * Adapt the array content and make it match the declared size
 * by removing items
 * 
 * 
 */
	@Fix(DivineValidator.INVALID_ARRAY_CONTENT_EXEED_SIZE)
	def adapt_arrayContent_ToSize(Issue issue, IssueResolutionAcceptor acceptor) {
		acceptor.accept(issue, "remove element to initial array values ", "Change array Content to match the array size declaration", null,
			([ EObject element, IModificationContext context |
				
					if(element instanceof Array)
				{
                   var	Array array =  element as Array ; 
					var nbElementsToRemove =   array.initValue.size() - array.size; 
				var int	 taille =0; 
					for(i :0 ..nbElementsToRemove-1)
					{
						taille = array.initValue.size;
						array.initValue.remove(taille-1);
					}
				
				
				} else {
					System.err.println("Not yet implemented");
					System.out.println("class error : " + element.getClass().getName());
				}
			]));

	

	}
	/**
	 * array content size > array size
	 * adapt the array size to it content size
	 * 
	 */

	@Fix(DivineValidator.INVALID_ARRAY_CONTENT_EXEED_SIZE)
	def adapt_ArraySize_ToContent_(Issue issue, IssueResolutionAcceptor acceptor) {
		acceptor.accept(issue, "Change array size to match content size ", "Change array size to match the array content size at initialization", null,
			([ EObject element, IModificationContext context |
				if (element instanceof Array) {
					var Array array = element as Array;

					if (array.initValue == null) {
						array.setSize(0);
					} else {
						array.setSize(array.initValue.size());
					}
				} else {
					System.err.println("Not yet implemented");
					System.out.println("Class source error :" + element.getClass().getName());
				}
			]));
	}

}
