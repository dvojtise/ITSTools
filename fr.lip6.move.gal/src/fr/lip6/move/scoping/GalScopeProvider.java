/*
 * generated by Xtext
 */
package fr.lip6.move.scoping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.naming.QualifiedName;
import org.eclipse.xtext.resource.IEObjectDescription;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.SimpleScope;
import org.eclipse.xtext.xbase.scoping.XbaseScopeProvider;

import com.google.common.base.Function;
import com.google.inject.Inject;


import fr.lip6.move.coloane.emf.Model.Tattribute;
import fr.lip6.move.coloane.emf.Model.Tnode;
import fr.lip6.move.gal.AbstractInstance;
import fr.lip6.move.gal.AbstractParameter;
import fr.lip6.move.gal.Call;
import fr.lip6.move.gal.CompositeTypeDeclaration;
import fr.lip6.move.gal.For;
import fr.lip6.move.gal.GALTypeDeclaration;
import fr.lip6.move.gal.GalInstance;
import fr.lip6.move.gal.InstanceCall;
import fr.lip6.move.gal.Interface;
import fr.lip6.move.gal.ItsInstance;
import fr.lip6.move.gal.Label;
import fr.lip6.move.gal.OtherInstance;
import fr.lip6.move.gal.SelfCall;
import fr.lip6.move.gal.Synchronization;
import fr.lip6.move.gal.TemplateInstance;
import fr.lip6.move.gal.Transient;
import fr.lip6.move.gal.Transition;

/**
 * This class contains custom scoping description.
 * 
 * see : http://www.eclipse.org/Xtext/documentation/latest/xtext.html#scoping
 * on how and when to use it 
 *
 */
public class GalScopeProvider extends XbaseScopeProvider {

	@Inject
	static ITSQualifiedNameProvider nameProvider;


	public static IScope sgetScope (EObject context, EReference reference) {
		String clazz = reference.getEContainingClass().getName() ;
		String prop = reference.getName();
		
		if ("Call".equals(clazz) && "label".equals(prop)) {
			if (context instanceof Call) {
				Call call = (Call) context;

				Transition p = getOwningTransition(call);
				List<Label> labs= new ArrayList<Label>();
				Set<String> seen = new HashSet<String>();
				GALTypeDeclaration s = getSystem(context);
				if (s==null) {
					return null;
				}
				for (Transition t  : s.getTransitions()) {
					if (t!=p && t.getLabel() != null && ! seen.contains(t.getLabel().getName())) {
						labs.add(t.getLabel());
						seen.add(t.getLabel().getName());
					}
				}
				return Scopes.scopeFor(labs) ;
			}
		} else if ("VariableRef".equals(clazz) && "referencedVar".equals(prop)) {
			if (getOwningTransition(context)==null && ! isTransientPredicate(context)) {
				return IScope.NULLSCOPE;
			}
			GALTypeDeclaration s = getSystem(context);
			if (s==null) {
				return null;
			}
			return Scopes.scopeFor(s.getVariables());
		} else if ("ArrayVarAccess".equals(clazz) && "prefix".equals(prop)) {
			if (getOwningTransition(context)==null && ! isTransientPredicate(context)) {
				return IScope.NULLSCOPE;
			}
			GALTypeDeclaration s = getSystem(context);
			if (s==null) {
				return null;
			}
			return Scopes.scopeFor(s.getArrays());
		} else if (clazz.contains("ParamRef") && "refParam".equals(prop)) {
			Transition t = getOwningTransition(context);
			GALTypeDeclaration s = getSystem(context);
			if (s==null) {
				return null;
			}
			if (t==null)
				return Scopes.scopeFor(s.getParams());
			List<AbstractParameter> union = new ArrayList<AbstractParameter>(s.getParams());
			union.addAll(t.getParams());
			// add any (nested ?) for loop parameters
			EObject parent = context.eContainer();
			while (parent != t) {
				if (parent instanceof For) {
					union.add(((For)parent).getParam());
				}
				parent = parent.eContainer();
			}

			return Scopes.scopeFor(union);
		} else if (context instanceof SelfCall && "label".equals(prop)) {
			SelfCall selfcall = (SelfCall) context;
			EList<Synchronization> a = ((CompositeTypeDeclaration) selfcall.eContainer().eContainer()).getSynchronizations();
			Set<Label> toScope = new HashSet<Label>();
			for (Synchronization t : a){
				if (t.getLabel() != null && ((Synchronization) selfcall.eContainer()).getLabel() != t.getLabel()){
					toScope.add(t.getLabel());
				}
			}
			return Scopes.scopeFor(toScope) ;
		} else if (context instanceof InstanceCall && "label".equals(prop) ){
			InstanceCall call = (InstanceCall) context;
			AbstractInstance inst = call.getInstance();
			if (inst instanceof GalInstance) {
				GalInstance gal = (GalInstance) inst;
				EList<Transition> a = gal.getType().getTransitions();
				List<Label> toScope = new ArrayList<Label>();
				for (Transition t : a){
					if (t.getLabel() != null){
						toScope.add(t.getLabel());
					}
				}
				return Scopes.scopeFor(toScope) ;

			} else if (inst instanceof ItsInstance) {
				ItsInstance its = (ItsInstance) inst;
				Set<Label> toScope = new HashSet<Label>();
				for (Synchronization t : its.getType().getSynchronizations()){
					if (t.getLabel() != null){
						toScope.add(t.getLabel());
					}
				}
				return Scopes.scopeFor(toScope) ;

			} else if (inst instanceof OtherInstance) {
				OtherInstance other = (OtherInstance) inst;
				ArrayList<Tattribute> toScope = new ArrayList<Tattribute>();
				if (other.getType().getNodes() == null){
					return null;
				}
				QualifiedName name = nameProvider.getFullyQualifiedName(other.getType());
				for (Tnode node: other.getType().getNodes().getNode()){
					EList<Tattribute> attss = node.getAttributes().getAttribute();
					for (Tattribute att : attss){
						//							if (att.getName().equals("label")) {
						//								EObject oatts = att.eContainer();
						//								if (oatts instanceof Tattributes) {
						//									Tattributes atts = (Tattributes) oatts;
						//									for (Tattribute iatt :atts.getAttribute()) {
						//										if (iatt.getName().equals("visibility")) {
						//											if ( "PUBLIC".equalsIgnoreCase(iatt.getValue())) {
						//												
						//												toScope.add(att);
						//												break;
						//											} else {
						//												break;
						//											}
						//										}
						//									}
						//								}
						if (nameProvider.getFullyQualifiedName(att) != null){
							toScope.add(att);
						}
					}
				}

				return Scopes.scopeFor(toScope, new Function<EObject, QualifiedName>() {
					public QualifiedName apply(EObject o){
						return nameProvider.getFullyQualifiedName(o);							
					}
				}, IScope.NULLSCOPE);
			} else if (inst instanceof TemplateInstance) {
				TemplateInstance ti = (TemplateInstance) inst;
				List<Label> labels = new ArrayList<Label>();
				for (Interface itf  :ti.getType().getInterfaces()) {
					labels.addAll(itf.getLabels());
				}
				return Scopes.scopeFor(labels);
			}

		} 

		return null;
	}

	public IScope getScope(EObject context, EReference reference) {
		IScope res = sgetScope(context, reference);
		if (res == null) {

			if (context instanceof ItsInstance && "type".equals(reference.getName())){
				IScope scope = super.getScope(context, reference);
				Iterable<IEObjectDescription> it = scope.getAllElements();
				Set<IEObjectDescription> toScope = new HashSet<IEObjectDescription>();
				for (IEObjectDescription ieo : it){				
					if (false == ((CompositeTypeDeclaration)context.eContainer()).getName().equals(ieo.getQualifiedName().toString())){
						toScope.add(ieo);
					}
				}
				return new SimpleScope(toScope);
			}	


			return super.getScope(context, reference);
		} else {
			return res;
		}
	}

	public static Transition getOwningTransition(EObject call) {
		EObject parent = call.eContainer();
		while (parent != null && !(parent instanceof fr.lip6.move.gal.Specification)) {
			if (parent instanceof Transition) {
				return (Transition) parent;
			}
			parent = parent.eContainer();
		}

		// should not happen
		return null;
	}


	public static boolean isTransientPredicate (EObject call) {
		EObject parent = call.eContainer();
		while (parent != null && !(parent instanceof fr.lip6.move.gal.GALTypeDeclaration)) {
			if (parent instanceof Transient) {
				return true;
			} 
			parent = parent.eContainer();
		}
		return false;
	}

	public static GALTypeDeclaration getSystem(EObject call) {
		EObject parent = call.eContainer();
		while (parent != null && !(parent instanceof GALTypeDeclaration)) {
			parent = parent.eContainer();
		}

		return (GALTypeDeclaration) parent;
	}
}
