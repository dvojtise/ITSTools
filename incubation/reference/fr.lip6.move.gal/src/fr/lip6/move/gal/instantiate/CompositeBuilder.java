package fr.lip6.move.gal.instantiate;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import fr.lip6.move.gal.InstanceDecl;
import fr.lip6.move.gal.InstanceDeclaration;
import fr.lip6.move.gal.Statement;
import fr.lip6.move.gal.And;
import fr.lip6.move.gal.ArrayPrefix;
import fr.lip6.move.gal.Assignment;
import fr.lip6.move.gal.BooleanExpression;
import fr.lip6.move.gal.ComparisonOperators;
import fr.lip6.move.gal.CompositeTypeDeclaration;
import fr.lip6.move.gal.Constant;
import fr.lip6.move.gal.False;
import fr.lip6.move.gal.GALTypeDeclaration;
import fr.lip6.move.gal.GalFactory;
import fr.lip6.move.gal.GF2;
import fr.lip6.move.gal.InstanceCall;
import fr.lip6.move.gal.IntExpression;
import fr.lip6.move.gal.Label;
import fr.lip6.move.gal.Parameter;
import fr.lip6.move.gal.SelfCall;
import fr.lip6.move.gal.Specification;
import fr.lip6.move.gal.Synchronization;
import fr.lip6.move.gal.Transition;
import fr.lip6.move.gal.True;
import fr.lip6.move.gal.TypedefDeclaration;
import fr.lip6.move.gal.Util;
import fr.lip6.move.gal.VarDecl;
import fr.lip6.move.gal.Variable;
import fr.lip6.move.gal.VariableReference;
import fr.lip6.move.gal.order.CompositeGalOrder;
import fr.lip6.move.gal.order.IOrder;
import fr.lip6.move.gal.order.IOrderVisitor;
import fr.lip6.move.gal.order.OrderFactory;
import fr.lip6.move.gal.order.VarOrder;

public class CompositeBuilder {


	private static CompositeBuilder instance = new CompositeBuilder();

	private CompositeBuilder() {}

	public static CompositeBuilder getInstance() {
		return instance;
	}

	private GALTypeDeclaration gal=null;
	// a cache holding total number of variables
	private int galSize=-1;


	public void decomposeWithOrder (GALTypeDeclaration galori, IOrder order) {
		gal = galori ; 
		Specification spec = (Specification) gal.eContainer(); 

		GALRewriter.flatten(spec, true);

		if (gal.getTransient() != null && ! (gal.getTransient().getValue() instanceof False)) {
			// skip, we don't know how to handle transient currently
			return ;
		}

		Partition p = new Partition(order);

		System.err.println("Partition obtained :" + p);

		CompositeTypeDeclaration ctd = galToCompositeWithPartition(spec, p);
		
		rewriteComposite(order , ctd);
		
		spec.setMain(ctd);
		Simplifier.simplify(spec);

		TypeFuser.fuseSimulatedTypes(spec);
		
		
		gal = null;
		galSize = -1 ;		
		
	}
	
	
	public Specification buildComposite (GALTypeDeclaration galori, String path) {

		gal = galori ; 
		Specification spec = (Specification) gal.eContainer(); //GalFactory.eINSTANCE.createSpecification();


		Map<VarDecl, Set<Integer>> domains = DomainAnalyzer.computeVariableDomains(gal);
		//		for (Entry<VarDecl, Set<Integer>> entry : domains.entrySet()) {
		//			if (entry.getKey() instanceof Variable) {
		//				Variable var = (Variable) entry.getKey();
		//				if (entry.getValue().size() < 3 && HotBitRewriter.isContinuous(entry.getValue())) {
		//					//if (var.getName().contains("chan"))
		//					rewriteUsingDomain(var,entry.getValue(),gal);
		//				}
		//			}
		//		}
		GALRewriter.flatten(spec, true);
		//		if (true)
		//		return spec;		
		if (gal.getTransient() != null && ! (gal.getTransient().getValue() instanceof False)) {
			// skip, we don't know how to handle transient currently
			return spec;
		}
		Partition p = buildPartition();

		System.err.println("Partition obtained :" + p);


		List<ArrayPrefix> totreat = new ArrayList<ArrayPrefix> ();
		for (ArrayPrefix ap : gal.getArrays()) {
			// create a dummy array ref to use the getVarIndex API
			Variable v = GalFactory.eINSTANCE.createVariable();
			VariableReference vref = GF2.createVariableRef(v);
			VariableReference ava = GF2.createArrayVarAccess(ap, vref);

			// a target list representing the whole array
			TargetList tl = new TargetList();
			tl.addAll(getVarIndex(ava));

			for (TargetList tp : p.getParts()) {
				if (tp.intersects(tl) &&  ! tp.contains(tl)) {
					// so a partition element overlaps only part of the array
					// this can only happen if the array is only referred to statically in the whole spec
					// rewrite it
					totreat.add(ap);
					break;
				}
			}			
		}
		for (ArrayPrefix ap : totreat) {
			System.err.println("Rewriting array " + ap.getName()+ " to variables to allow decomposition.");
			rewriteArrayAsVariables (ap);
		}

		// attempt some normalizations of variable and transition order 
		Orderer.lexicoSortGALVariables(gal);
		
		if (!totreat.isEmpty()) {
			galSize=-1;
			// we may have some partially constant arrays that could be simplified out
			Simplifier.simplify(gal);
			p = buildPartition();
		}

		printDependencyMatrix(gal, p, path);
		IOrder order = null;
		try {
			order = OrderFactory.parseOrder(path.replace(".txt", ".gtr"), p.parts.size());
			System.out.println(order);

		} catch (IOException e) {
			System.err.println("No order file " + path.replace(".txt", ".gtr") + " found. Skipping load order phase.");
		}


		CompositeTypeDeclaration ctd = galToCompositeWithPartition(spec, p);
		
	//	rewriteComposite(order , ctd);
		
		spec.setMain(ctd);
		Simplifier.simplify(spec);

		TypeFuser.fuseSimulatedTypes(spec);
		
		
		gal = null;
		galSize = -1 ;

		//		printDependencyMatrix(ctd,path);
		return spec;
	}

	private CompositeTypeDeclaration galToCompositeWithPartition(
			Specification spec, Partition p) {
		spec.getTypes().remove(gal);

		// create a GAL type to hold the variables and transition parts of each partition element
		for (int pindex = 0; pindex < p.getParts().size() ; pindex++) {
			GALTypeDeclaration pgal = GalFactory.eINSTANCE.createGALTypeDeclaration();
			pgal.setName("p"+pindex);
			spec.getTypes().add(pgal);
		}

		CompositeTypeDeclaration ctd = GalFactory.eINSTANCE.createCompositeTypeDeclaration();
		String cname = gal.getName()+"_mod";
		cname = cname.replaceAll("\\.", "_");
		ctd.setName(cname);
		spec.getTypes().add(ctd);
		for (int i=0 ; i  < p.getParts().size() ; i++) {
			InstanceDeclaration gi = GF2.createInstance(spec.getTypes().get(i),"i"+i);
			ctd.getInstances().add(gi);
		}

		for (Transition t : gal.getTransitions()) {		
			// collect guard edges and statement edges.
			List<Edge<BooleanExpression>> guardEdges = new ArrayList<Edge<BooleanExpression>>();
			List<Edge<Statement>> actionEdges = new ArrayList<Edge<Statement>>();

			collectGuardTerms (t.getGuard(), guardEdges);
			for (Statement a : t.getActions()) {
				collectStatements (a,actionEdges);
			}
			// compute full support of transition
			TargetList support = new TargetList();
			for (Edge<BooleanExpression> edge : guardEdges) {
				support.targets.or(edge.targets.targets);
			}
			for (Edge<Statement> edge : actionEdges) {
				support.targets.or(edge.targets.targets);
			}

			Synchronization sync = GalFactory.eINSTANCE.createSynchronization();
			sync.setName(t.getName().replaceAll("\\.", "_"));
			if (t.getLabel() != null) {
				sync.setLabel(t.getLabel());
			} else {
				Label lab = GF2.createLabel("");
				sync.setLabel(lab);
			}
			ctd.getSynchronizations().add(sync);

			for (int pindex = 0 ; pindex < p.getParts().size() ; pindex++) {
				TargetList tl = p.parts.get(pindex);
				if (support.intersects(tl)) {
					GALTypeDeclaration galoc = (GALTypeDeclaration) spec.getTypes().get(pindex);

					Transition tloc = GalFactory.eINSTANCE.createTransition();
					tloc.setName(t.getName());
					Label lab = GF2.createLabel(t.getName());
					tloc.setLabel(lab);

					InstanceCall icall = GF2.createInstanceCall(GF2.createVariableRef(ctd.getInstances().get(pindex)),lab);
					sync.getActions().add(icall);

					BooleanExpression guard = GalFactory.eINSTANCE.createTrue();

					for (Edge<BooleanExpression> edge : guardEdges) {
						if (edge.targets.intersects(tl)) {
							guard = GF2.and(guard, edge.expression);
						}
					}
					tloc.setGuard(guard);

					for (Edge<Statement> edge : actionEdges) {
						if (edge.targets.intersects(tl)) {
							tloc.getActions().add(edge.expression);
						}
					}

					galoc.getTransitions().add(tloc);
				}
			}

			// add any remaining calls to labels as self calls
			List<Statement> todrop = new ArrayList<Statement>();
			for (Statement a : t.getActions()) {
				if (a instanceof SelfCall) {
					SelfCall scall = EcoreUtil.copy((SelfCall) a);
					sync.getActions().add(scall);
					todrop.add(a);
				}
			}
			t.getActions().removeAll(todrop);

		}

		Map<Variable,Integer> varmap = new HashMap<Variable, Integer>();
		for (Variable var : gal.getVariables()) {
			varmap.put(var, p.getIndex(var));
		}
		Map<ArrayPrefix,Integer> arrmap = new HashMap<ArrayPrefix, Integer>();
		for (ArrayPrefix ap : gal.getArrays()) {
			arrmap.put(ap, p.getIndex(ap));
		}

		for (Entry<Variable, Integer> entry : varmap.entrySet()) {
			GALTypeDeclaration galloc = (GALTypeDeclaration) spec.getTypes().get(entry.getValue());
			galloc.getVariables().add(entry.getKey());
		}

		for (Entry<ArrayPrefix, Integer> entry : arrmap.entrySet()) {
			GALTypeDeclaration galloc = (GALTypeDeclaration) spec.getTypes().get(entry.getValue());
			galloc.getArrays().add(entry.getKey());
		}		


		for (Transition t : gal.getTransitions()) {
			t.setGuard(GalFactory.eINSTANCE.createTrue());

		}


		PlaceTypeSimplifier.collapsePlaceType(spec);
		return ctd;
	}

	/**
	 * Recursively decompose a Composite to an semantically equivalent type with hierarchical nesting
	 * corresponding to order. 
	 * @param order A hierarchical ordering that maps ALL instance names of ctd (tested by assertion).
	 * @param ctd the type to rewrite, in place.
	 */
	private void rewriteComposite(IOrder order, CompositeTypeDeclaration ctd) {
		// 
		if (order != null && order instanceof CompositeGalOrder) {
			Map<String, Integer> varMap = new HashMap<String, Integer>();
			CompositeGalOrder cgo = (CompositeGalOrder) order;
			if (cgo.getChildren().size() > 1) {
				for (int i = 0 ; i < cgo.getChildren().size() ; i++) {
					IOrder child = cgo.getChildren().get(i);
					for (String var: child.getAllVars()) {
						varMap.put(var, i);
					}
				}
				List<InstanceDecl> subs = rewriteComposite(varMap, ctd);
				for (int i = 0 ; i < cgo.getChildren().size() ; i++) {
					if (subs.get(i).getType() instanceof CompositeTypeDeclaration) {
						rewriteComposite(cgo.getChildren().get(i), (CompositeTypeDeclaration) subs.get(i).getType());
					}
				}
			}
		}		
	}

	/** Implement :
	 * Avec les domaines connus, on peut réecrire le code :
t [ x != y ] {
     x = y;
}
Si x et y variables booléennes, on peut faire typedef bool_t = 0..1; t
($x:bool_t)  [ $x != y  && x == $x ] {
     x = y;
}
puis :
t ($x:bool_t, y:bool_t)  [ $x != $y  && x == $x && y==$y ] {
     x = $y;
}
Les paramètres sont ensuite instanciés (produit cartésien des
possibles), on simplifie ce qui peut l'être et on a :
t_0_1  [ x == 0 && y== 1 ] {
     x = 1;
}
t_1_0  [ x == 1 && y==0 ] {
     x = 0;
}
	 * @param key
	 * @param set
	 * @param gal2
	 */
	private void rewriteUsingDomain(Variable targetVar, Set<Integer> set, GALTypeDeclaration gal) {

		TypedefDeclaration typedef = GalFactory.eINSTANCE.createTypedefDeclaration();
		typedef.setMin(GF2.constant(Collections.min(set)));
		typedef.setMax(GF2.constant(Collections.max(set)));
		typedef.setName(targetVar.getName().replaceAll("\\.", "_") + "_t");
		typedef.setComment("/** For domain of "+ targetVar.getName() + " */");

		gal.getTypes().add(typedef);

		for (Transition t : gal.getTransitions()) {

			List<VariableReference> concernsVar = new ArrayList<VariableReference>();
			for (EObject obj : Util.getAllChildren(t)) {
				if (obj instanceof VariableReference) {
					VariableReference vref = (VariableReference) obj;
					if (vref.eContainer() instanceof Assignment 
							&& vref.eContainingFeature().getName().equals("left"))
						continue ;

					if (vref.getRef() == targetVar) {
						concernsVar.add(vref);	
					}
				}
			}
			if (! concernsVar.isEmpty()) {
				Parameter p = GalFactory.eINSTANCE.createParameter();
				p.setName("$"+targetVar.getName().replaceAll("\\.", "_"));
				p.setType(typedef);

				t.getParams().add(p);


				// create x == $x
				BooleanExpression cmp = GF2.createComparison(GF2.createVariableRef(targetVar), ComparisonOperators.EQ, GF2.createParamRef(p));

				t.setGuard(GF2.and(t.getGuard(),cmp));


				for (VariableReference v : concernsVar) {
					EcoreUtil.replace(v, GF2.createParamRef(p));
				}

			}

		}


	}


	private void printDependencyMatrix(GALTypeDeclaration gal2, Partition p,
			String path) {
		// TODO Auto-generated method stub
		final int [][] deps = new int [p.parts.size()][gal.getTransitions().size()];

		for (int i = 0; i < gal.getTransitions().size() ; i++) {
			Transition t = gal.getTransitions().get(i);
			// collect guard edges and statement edges.
			List<Edge<BooleanExpression>> guardEdges = new ArrayList<Edge<BooleanExpression>>();
			List<Edge<Statement>> actionEdges = new ArrayList<Edge<Statement>>();

			collectGuardTerms (t.getGuard(), guardEdges);
			for (Statement a : t.getActions()) {
				collectStatements (a,actionEdges);
			}
			// compute full support of transition
			TargetList support = new TargetList();
			for (Edge<BooleanExpression> edge : guardEdges) {
				support.targets.or(edge.targets.targets);
			}
			for (Edge<Statement> edge : actionEdges) {
				support.targets.or(edge.targets.targets);
			}

			for (Integer target : support) {
				deps[target][i] = 1;
			}
		}
		
		final List<Integer> permslines = new ArrayList<Integer>(deps.length);
		for (int i=0; i < deps.length ; i++) {
			permslines.add(i);
		}
		final List<Integer> permscols = new ArrayList<Integer>(deps.length);
		for (int i=0; i < deps[0].length ; i++) {
			permscols.add(i);
		}
//		Collections.sort(permslines, new Comparator<Integer>() {
//
//			@Override
//			public int compare(Integer i1, Integer i2) {
//				for (int j=0; j < deps[0].length ; j++) {
//					int cmp = new Integer(deps[i1][permscols.get(j)]).compareTo(deps[i2][permscols.get(j)]);
//					if (cmp != 0) {
//						return -cmp;
//					}
//				}
//				return 0;
//			}
//		
//		});
		
		Collections.sort(permscols, new Comparator<Integer>() {

			@Override
			public int compare(Integer i1, Integer i2) {
				int lasti1 = -1;
				for (int j=deps.length-1 ; j >= 0 ; j--) {
					if (deps[permslines.get(j)][i1] != 0) {
						lasti1 = j;
						break;
					}
				}
				int lasti2 = -1;
				for (int j=deps.length-1 ; j >= 0 ; j--) {
					if (deps[permslines.get(j)][i2] != 0) {
						lasti2 = j;
						break;
					}
				}
				if (lasti1 != lasti2)
					return new Integer(lasti1).compareTo(lasti2);

				int firsti1=0;
				for (int j=0 ; j  < deps.length ; j++) {
					if (deps[permslines.get(j)][i1] != 0) {
						firsti1 = j;
						break;
					}
				}
				int firsti2=0;
				for (int j=0 ; j  < deps.length ; j++) {
					if (deps[permslines.get(j)][i2] != 0) {
						firsti2 = j;
						break;
					}
				}
				return new Integer(firsti1).compareTo(firsti2);
				
//				int s1 =0;
//				int s2 =0;
//				for (int j=0; j < deps.length ; j++) {
//					s1 += deps[j][i1];
//					s2 += deps[j][i2];
//				}
//				return new Integer(s1).compareTo(s2);
////					int cmp = new Integer(deps[permslines.get(j)][i1]).compareTo(deps[permslines.get(j)][i2]);
//					
////				for (int j=0; j < deps.length ; j++) {
////					int cmp = new Integer(deps[permslines.get(j)][i1]).compareTo(deps[permslines.get(j)][i2]);
////					if (cmp != 0) {
////						return -cmp;
////					}
////				}
////				return 0;
			}
		
		});

		
		
		try {
			File pathff = new File(path);
			PrintStream trace = new PrintStream(pathff);
			// title line 
			trace.append("Variable");
			for (int ti = 0 ; ti < gal.getTransitions().size() ; ti++) {
				trace.append("\t"+ gal.getTransitions().get(permscols.get(ti)).getName());
			}
			trace.append("\n");
			// data lines
			for (int j = 0 ; j < p.parts.size() ; j++)  {
				trace.append("p"+permslines.get(j));
				for (int i = 0; i < gal.getTransitions().size() ; i++) {
					trace.append("\t"+deps[permslines.get(j)][permscols.get(i)]);
				}
				trace.append("\n");
			}
			trace.append("\n");
			trace.close();
		} catch (IOException e) {
			System.err.println("Could not write dependency matrix to file : "+path);
		}

	}

	private void printDependencyMatrix(CompositeTypeDeclaration ctd, String path) {

		int [][] deps = new int [ctd.getInstances().size()][ctd.getSynchronizations().size()];

		for (int i = 0; i < ctd.getSynchronizations().size() ; i++) {
			Synchronization synci = ctd.getSynchronizations().get(i);
			for (Statement a : synci.getActions()) {
				if (a instanceof InstanceCall) {
					InstanceCall icall = (InstanceCall) a;
					deps[ctd.getInstances().indexOf(icall.getInstance())][i] = 1;
				}
			}
		}
		try {
			File pathff = new File(path);
			PrintStream trace = new PrintStream(pathff);
			// title line 
			trace.append("Variable");
			for (Synchronization s : ctd.getSynchronizations()) {
				trace.append("\t"+s.getName());
			}
			trace.append("\n");
			// data lines
			int j=0;
			for (InstanceDecl instance : ctd.getInstances()) {
				trace.append(instance.getName());
				for (int i = 0; i < ctd.getSynchronizations().size() ; i++) {
					trace.append("\t"+deps[j][i]);
				}
				trace.append("\n");
				j++;
			}
			trace.append("\n");
			trace.close();
		} catch (IOException e) {
			System.err.println("Could not write dependency matrix to file : "+path);
		}
	}

	private Partition buildPartition() {
		Partition p = new Partition();
		for (Transition t : gal.getTransitions()) {		
			// collect guard edges and statement edges.
			List<Edge<BooleanExpression>> guardEdges = new ArrayList<Edge<BooleanExpression>>();
			List<Edge<Statement>> actionEdges = new ArrayList<Edge<Statement>>();

			collectGuardTerms (t.getGuard(), guardEdges);
			for (Statement a : t.getActions()) {
				collectStatements (a,actionEdges);
			}

			for (Edge<BooleanExpression> edge : guardEdges) {
				p.addRelation(edge.targets);
			}
			for (Edge<Statement> edge : actionEdges) {
				p.addRelation(edge.targets);
			}

		}
		return p;
	}


	/**
	 * Rewrite a composite type using a partition that maps each of its contained instances to an integer in "indexes".
	 * This decomposition rewrites c to a composite with the same semantics as c, but with only |indexes| subcomponents.
	 * @param varMap a partition of c's instances, mapping instance names to integers. All of c's instances should be covered (tested by an assert).
	 * @param c a composite type declaration that will be modified in place by the procedure
	 * @return a list of |indexes| abstractinstances 
	 */
	private List<InstanceDecl> rewriteComposite (Map<String,Integer> varMap, CompositeTypeDeclaration c) {

		// grab the indexes assigned to each instance of type
		Set<Integer> indexes = new TreeSet<Integer> (varMap.values());
		//  a reverse map to count instances per partition element
		Map<Integer,List<String>> revMap = new HashMap<Integer, List<String>>();
		for (Entry<String, Integer> e : varMap.entrySet()) {
			List<String> list = revMap.get(e.getValue());
			if (list == null) {
				list = new ArrayList<String>();
				revMap.put(e.getValue(), list);
			}
			list.add(e.getKey());
		}
		// to hold the new set of instances, one per index/element of the partition
		List<InstanceDecl> subs = new ArrayList<InstanceDecl>();
		// a clean copy of the current instances : these will be progressively pushed to new subtypes 
		List<InstanceDecl> current = new ArrayList<InstanceDecl>(c.getInstances());
		// for each partition element
		for (int i : indexes) {
			
			// test for trivial single instance case
			if (revMap.get(i).size() == 1) {
				// to keep indexes consistent
				subs.add(null);
				// we can avoid creating a Composite
				continue;
			}
			// create a new Composite type to hold the instances that it will be responsible for
			CompositeTypeDeclaration sub = GalFactory.eINSTANCE.createCompositeTypeDeclaration();
			sub.setName(c.getName()+"_"+i);
			// add it to enclosing specification
			((Specification)c.eContainer()).getTypes().add(sub);

			// an instance of this partition element
			InstanceDeclaration inst = GF2.createInstance(sub, "i"+i);
			inst.setType(sub);
			// add it to instances
			c.getInstances().add(inst);
			// add it to subs
			subs.add(inst);
		}
		
		for (InstanceDecl ai : current) {
			Integer pindex = varMap.get(ai.getName());
			assert ( pindex != null );
			// test for trivial single instance case
			if (revMap.get(pindex).size() == 1) {
				// patch subs
				subs.set(pindex, ai);
				continue;
			} else {
				((CompositeTypeDeclaration)subs.get(pindex).getType()).getInstances().add(ai);
			}
		}
		int nblocal=0;
		int nbavoid=0;
		// for syncs deleted since purely local after transfo
		List<Synchronization> todel = new ArrayList<Synchronization>();
		// rewrite syncs one by one
		for (Synchronization s : c.getSynchronizations()) {
			// map each instance (through its index) to its effect part, null initially.
			Map<Integer,Synchronization> effects = new HashMap<Integer, Synchronization>();
			// if sync has a self call, we can't destroy it even if it looks local
			// TODO : if ALL the call targets are also local to same target, maybe we could push the whole thing down.
			// TODO : There is a potential commutativity issue if some of self.called effects do not commute with local iX.call effects. 
			// They end up being unsorted with the current algorithm. Should test for this situation and build several labels for effect parts to patch.
			boolean hasSelfCall = false;
			Set<Integer> targets = new HashSet<Integer>();
			// For each action in the sync
			for (Statement a : new ArrayList<Statement>(s.getActions())) {
				if (a instanceof InstanceCall) {
					// So found a call of the form :  i."laba"
					InstanceCall ic = (InstanceCall) a;
					// grab index of i
					Integer pindex = varMap.get(((VariableReference)ic.getInstance()).getRef().getName());
					// everybody got indexed before start of loop
					assert ( pindex != null );
					// for locality test at end of actions list
					targets.add(pindex);

					// test for trivial single instance case
					if (revMap.get(pindex).size() == 1) {
						continue;
					}
					
					// Look if we already have effects for this component
					Synchronization ssub = effects.get(pindex);
					
					// miss !
					if (ssub == null) {
						// no effects yet : create a new empty sync, with label "" (hoping it is local)
						ssub = GalFactory.eINSTANCE.createSynchronization();
						// name is set to same sync name
						ssub.setName(s.getName());
						ssub.setLabel(null);
						// add it to effects map for hits on subsequent effects
						effects.put(pindex, ssub);
					}
					
					// move current action a."lab" to effects on subcomponent sync body
					ssub.getActions().add(a);
				} else {
					// found a self call; mark it
					hasSelfCall = true;
				}
				// currently no other instruction types in Composite
				// TODO : we need deeper treatments akin to support computation of GAL to correctly treat nested statements
			}
			
			
			// test if all effects of this local sync (label "") were local to a single target and there were no self calls
			if (false) {
//			if (s.getLabel().getName().equals("") 
//				&& targets.size() == 1 
//				&& ! hasSelfCall) {
				
				// we need to build proper labels for each subeffect and call these labels in resulting sync
				for (Entry<Integer, Synchronization> entry : effects.entrySet()) {
					// set local label
					entry.getValue().setLabel(GF2.createLabel(""));
					
					
					String n1 = entry.getValue().getName();
					entry.getValue().setName(null);
					Synchronization target = null;
					for (Synchronization s2 : ((CompositeTypeDeclaration) subs.get(entry.getKey()).getType()).getSynchronizations()) {
						String n2 = s2.getName();
						s2.setName(null);
						if (EcoreUtil.equals(s2, entry.getValue())) {
							target = s2;
						}
						s2.setName(n2);
						if (target != null)
							break;
					}
					entry.getValue().setName(n1);

					if (target != null) {
						nblocal++;
					} else {
						//add it to instance type
						((CompositeTypeDeclaration)subs.get(entry.getKey()).getType()).getSynchronizations().add(entry.getValue());
					}
				}
				// destroy owner (outside loop)
				todel.add(s);
			} else {
				// we need to build proper labels for each subeffect and call these labels in resulting sync
				for (Entry<Integer, Synchronization> entry : effects.entrySet()) {
					// a new label with the (unique) name of the current sync
					Label lab = GF2.createLabel(s.getName());
					
					String n1 = entry.getValue().getName();
					entry.getValue().setName(null);
					Synchronization target = null;
					for (Synchronization s2 : ((CompositeTypeDeclaration) subs.get(entry.getKey()).getType()).getSynchronizations()) {
						String n2 = s2.getName();
						Label l2 = s2.getLabel();
						if (l2.getName().equals("")) {
							continue;
						}
						s2.setName(null);
						s2.setLabel(null);
						if (EcoreUtil.equals(s2, entry.getValue())) {
							target = s2;
						}
						s2.setName(n2);
						s2.setLabel(l2);
						if (target != null)
							break;
					}
					entry.getValue().setName(n1);

					if (target != null) {
						nbavoid++;
						lab = target.getLabel();
					} else {
						// set it to current effects 
						entry.getValue().setLabel(lab);
						//add it to instance type
						((CompositeTypeDeclaration) subs.get(entry.getKey()).getType()).getSynchronizations().add(entry.getValue());
					}

					
					
					// create a call to appropriate instance, to the new label
					InstanceCall icall = GalFactory.eINSTANCE.createInstanceCall();
					icall.setInstance(GF2.createVariableRef(subs.get(entry.getKey())));
					icall.setLabel(lab);
					
					// add the new call action to the current sync
					s.getActions().add(icall);
				}
			}

		}
		if (nbavoid+nblocal > 0)
			System.err.println("Avoided creation "+nblocal+" of redundant local effect and "+nbavoid + " effects.");
		
		// these are syncs that were identified as purely local to some subcomponent
		c.getSynchronizations().removeAll(todel);

		return subs;
	}

	/**
	 * Goal is to rewrite all accesses to array ap as accesses to a set of plain variables.
	 * It's supposed to be possible, i.e. all accesses are actually of the form ap[Constant].
	 * @param ap
	 */
	private void rewriteArrayAsVariables(ArrayPrefix ap) {

		// Pickup all accesses to the array in the spec
		List<VariableReference> totreat = new ArrayList<VariableReference>();
		// a first pass to collect without causing concurrent modif exception
		for (TreeIterator<EObject> it = gal.eAllContents(); it.hasNext() ; ) {
			EObject obj = it.next();
			if (obj instanceof VariableReference) {
				VariableReference ava = (VariableReference) obj;
				if (ava.getRef() == ap) {
					if ( ava.getIndex() instanceof Constant) {
						totreat.add(ava);
					} else {
						// abort !!
						return;
					}
				}
			}
		}

		// replacements for ava
		List<VariableReference> vrefs = new ArrayList<VariableReference>();
		// build the new set of variables, and refs upon them.
		int index = 0;
		for (IntExpression value : ap.getValues()) {
			Variable vi = GalFactory.eINSTANCE.createVariable();
			vi.setName(ap.getName()+"_"+index++);
			vi.setValue(EcoreUtil.copy(value));
			gal.getVariables().add(vi);
			VariableReference vref = GF2.createVariableRef(vi);
			vrefs.add(vref);
		}
		System.err.println("Rewriting array :" + ap.getName() + " to a set of variables to improve separability.");

		// now replace
		for (VariableReference ava : totreat) {
			EcoreUtil.replace(ava, EcoreUtil.copy(vrefs.get(((Constant) ava.getIndex()).getValue())));
		}
		// kill ap now
		gal.getArrays().remove(ap);
	}

	private void collectStatements(Statement a, List<Edge<Statement>> actionEdges) {

		TargetList tlist = new TargetList();

		for ( TreeIterator<EObject> it = a.eAllContents(); it.hasNext() ; ) {
			EObject obj = it.next();
			if (obj instanceof VariableReference) {
				List<Integer> targets = getVarIndex((VariableReference) obj);
				tlist.addAll(targets);
				it.prune();
			}				
		}
		actionEdges.add(new Edge<Statement>(a, tlist));
	}

	/**
	 * Check that guard is a conjunction of conditions, and add the dependencies induced on parameters to them.
	 * @param guard
	 * @param guardEdges
	 * @return
	 */
	private boolean collectGuardTerms(BooleanExpression guard,	List<Edge<BooleanExpression>> guardEdges) {
		if (guard instanceof And) {
			And and = (And) guard;
			return collectGuardTerms(and.getLeft(), guardEdges) && collectGuardTerms(and.getRight(), guardEdges);				
		} else if (guard instanceof True) {
			return true;
		} else {
			TargetList tlist = new TargetList();

			for ( TreeIterator<EObject> it = guard.eAllContents(); it.hasNext() ; ) {
				EObject obj = it.next();
				if (obj instanceof VariableReference) {
					VariableReference va = (VariableReference) obj;
					List<Integer> targets = getVarIndex(va);
					tlist.addAll(targets);
					it.prune();
				}				
			}
			guardEdges.add(new Edge<BooleanExpression>(guard, tlist));
			return true;
		} 

		//return false;
	}



	private int getGalSize() {
		if (galSize == -1) {
			int sz = gal.getVariables().size();
			for (ArrayPrefix ap : gal.getArrays()) {
				sz += ap.getSize();
			}
			galSize = sz;
		}
		return galSize;
	}
	private List<Integer> getVarIndex(VariableReference ava) {
		if (ava.getIndex() == null) {
			return Collections.singletonList(gal.getVariables().indexOf(ava.getRef()));
		} else {
			int start = gal.getVariables().size();
			for (ArrayPrefix ap : gal.getArrays()) {
				if (ap != ava.getRef()) {
					start += ap.getSize();
				} else {
					break;
				}
			}
			if (ava.getIndex() instanceof Constant) {
				Constant cte = (Constant) ava.getIndex();
				return Collections.singletonList(start + cte.getValue());
			} else {
				if (ava.getRef() == null) {
					throw new NullPointerException("Array was destroyed !");
				}
				ArrayPrefix arr = (ArrayPrefix)ava.getRef();
				List<Integer> toret = new ArrayList<Integer>(arr.getSize());
				for (int i = 0 ; i < arr.getSize() ; i++) {
					toret.add(start + i);
				}
				return toret;
			}
		}
	}

	private String getVarName (int index) {
		if (index < gal.getVariables().size()) {
			return gal.getVariables().get(index).getName();
		} else {
			index -= gal.getVariables().size();
			for (ArrayPrefix ap : gal.getArrays()) {
				if (index < ap.getSize()) {
					return ap.getName() + "[" + index + "]";					
				} else {
					index -= ap.getSize();
				}
			}
		}
		return "OOB VARIABLE";
	}


	class TargetList implements Iterable<Integer>{
		private BitSet targets = new BitSet(getGalSize());

		public void addAll(List<Integer> more) {
			for (Integer i : more) {
				targets.set(i);
			}
		}

		int size() {
			return targets.cardinality();
		}

		public boolean contains (TargetList tl) {
			BitSet tmp = (BitSet) targets.clone();
			tmp.and(tl.targets);
			/** if a && b == a, b is superset of a */
			return tmp.equals(tl.targets);
		}

		@Override
		public String toString() { 
			StringBuilder sb = new StringBuilder();
			for (int i = targets.nextSetBit(0); i >= 0; i = targets.nextSetBit(i+1)) {
				sb.append(getVarName(i)+", ");
			}
			return sb.toString();
		}

		public boolean intersects(TargetList tl) {
			return targets.intersects(tl.targets);
		}

		@Override
		public boolean equals(Object obj) {
			return targets.equals(((TargetList)obj).targets);
		}

		public void add(int indexOf) {
			targets.set(indexOf);
		}

		@Override
		public Iterator<Integer> iterator() {
			return new TargetIterator();
		}

		private class TargetIterator implements Iterator<Integer> {
			int index = 0;

			@Override
			public boolean hasNext() {
				return targets.nextSetBit(index) != -1;
			}

			@Override
			public Integer next() {
				int toret = targets.nextSetBit(index);
				index = toret +1;
				return toret;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

		}

	}

	/** represent a partition of variables */
	class Partition {
		private List<TargetList> parts = new ArrayList<CompositeBuilder.TargetList>();

		public Partition (){}
		
		public Partition (IOrder ord) {
			parts = ord.accept(new IOrderVisitor<List<TargetList>>() {

				@Override
				public List<TargetList> visitComposite(CompositeGalOrder o) {
					List<TargetList> toret = new ArrayList<CompositeBuilder.TargetList>();
					for (IOrder sub : o.getChildren()) {
						toret.addAll(sub.accept(this));
					}
					return toret;
				}

				@Override
				public List<TargetList> visitVars(VarOrder varOrder) {
					TargetList toret = new TargetList();
					
					for (int i=0 ; i < getGalSize() ;  i++) {
						if (varOrder.getVars().contains(getVarName(i))) {
							toret.add(i);
						}
					}
					return Collections.singletonList(toret);
					
				}
			});
			
			
		}
		
		public List<TargetList> getParts() {
			return parts;
		}

		public Integer getIndex(ArrayPrefix ap) {
			TargetList tst = new TargetList();
			VariableReference dummy = GF2.createArrayVarAccess(ap, GF2.constant(0));
			tst.addAll(getVarIndex(dummy));

			for (int i = 0; i < parts.size(); i++) {
				if (parts.get(i).intersects(tst)) {
					return i;
				}
			}
			return -1;
		}

		public Integer getIndex(Variable var) {
			TargetList tst = new TargetList();
			tst.add(gal.getVariables().indexOf(var));
			for (int i = 0; i < parts.size(); i++) {
				if (parts.get(i).intersects(tst)) {
					return i;
				}
			}
			System.err.println("Could not find partition element corresponding to "+ var.getName() + " in partition " + this);
			return -1;
		}

		void addRelation (TargetList tl) {
			if (tl.targets.isEmpty())
				return;
			// we suppose parts is already a partition initially
			List<TargetList> newparts = new ArrayList<CompositeBuilder.TargetList>();
			for (TargetList part : parts) {
				if (part.equals(tl)) {
					/** never mind, this constraint tl is already taken into account */
					return;
				} else if (! part.intersects(tl)) {
					/** skip this entry */
					newparts.add(part);
				} else {

					if (part.contains(tl)) {
						/** we already have stronger constraint, stop here */
						return;
					} else {
						/** so we need to fuse into a stronger constraint */
						tl.targets.or(part.targets);
					}
				}
			}
			newparts.add(tl);
			parts = newparts;			
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			for (TargetList tl : parts) {
				sb.append("[" + tl + "],");
			}
			sb.append("\n");
			return sb.toString();
		}
	}

	class Edge<T> {
		T expression;
		TargetList targets;
		public Edge(T expression, TargetList targets) {
			this.expression = expression;
			this.targets = targets;
		}
		public TargetList getTargetList() {			
			return targets;
		}		

	}


	//	private static void addArrayAccessToTrans(
	//			Map<Transition, Map<ArrayPrefix, Set<Integer>>> arrEdges,
	//			Transition owner,
	//			ArrayVarAccess av) {
	//		Map<ArrayPrefix, Set<Integer>> arrmap = arrEdges.get(owner);
	//		if (arrmap == null) {
	//			arrmap = new HashMap<ArrayPrefix,Set<Integer>> ();
	//			arrEdges.put(owner, arrmap);
	//		}
	//		ArrayPrefix ap = av.getPrefix();
	//		Set<Integer> list = arrmap.get(ap);
	//		if (list == null) {
	//			list = new TreeSet<Integer>();
	//			arrmap.put(av.getPrefix(), list);
	//		}
	//		if (av.getIndex() instanceof Constant) {
	//			Constant cte = (Constant) av.getIndex();
	//			list.add(cte.getValue());						
	//		} else {
	////					hasComplexAccess.put(av.getPrefix(), true);Set<Integer> vals = new HashSet<Integer>();
	//			for (int i = 0 ; i < ap.getSize(); i++) {
	//				list.add(i);
	//			}
	//		}
	//	}
	//
	//	private static void addVarRefToTrans(
	//			Map<Transition, Set<Variable>> varEdges, Transition owner,
	//			VariableRef va) {
	//		Set<Variable> refs = varEdges.get(owner);
	//		if (refs == null) {
	//			refs = new HashSet<Variable>();
	//			varEdges.put(owner, refs);
	//		}
	//		refs.add(va.getReferencedVar());
	//	}
	//	
	//	private static<T> T pop(Set<T> todo) {
	//		T seed = todo.iterator().next();
	//		todo.remove(seed);
	//		return seed;
	//	}
	//	
	//	
	//	for (EObject obj : Instantiator.getAllChildren(gal)) {
	//		for (ArrayPrefix ap : gal.getArrays()) {
	//			Set<Integer> vals = new HashSet<Integer>();
	//			for (int i = 0 ; i < ap.getSize(); i++) {
	//				vals.add(i);
	//			}
	//		}			
	//	}
	//	
	//	// build hypergraph of transition to variable dependency
	//	Map<Transition, Set<Variable>> varEdges = new HashMap<Transition, Set<Variable>>();
	//	Map<Transition, Map<ArrayPrefix,Set<Integer>>> arrEdges = new HashMap<Transition, Map<ArrayPrefix,Set<Integer>>>();
	//
	//
	//
	//	Map<ArrayPrefix, Set<Integer>> constantArrs = new HashMap<ArrayPrefix, Set<Integer>>();
	//
	////	Map<ArrayPrefix,Boolean> hasComplexAccess = new HashMap<ArrayPrefix,Boolean>();
	//	
	////	int totalVars = gal.getVariables().size();		
	//	for (ArrayPrefix ap : gal.getArrays()) {
	//		Set<Integer> vals = new HashSet<Integer>();
	//		for (int i = 0 ; i < ap.getSize(); i++) {
	//			vals.add(i);
	//		}
	//		constantArrs.put(ap, vals);
	////		totalVars += ap.getSize();
	////		hasComplexAccess.put(ap, false);
	//	}
	//
	//	
	//	// compute hypergraph into Edges
	//	Transition owner = null;
	//	for (TreeIterator<EObject> it = gal.eAllContents() ; it.hasNext() ; ) {
	//		EObject obj = it.next();
	//		if (obj instanceof Transition) {
	//			owner = (Transition) obj;				
	//		} else if (obj instanceof VariableRef) {
	//			VariableRef va = (VariableRef) obj;
	//			addVarRefToTrans(varEdges, owner, va);
	//		} else if (obj instanceof ArrayVarAccess) {
	//			ArrayVarAccess av = (ArrayVarAccess) obj;
	//			addArrayAccessToTrans(arrEdges, owner, av);
	//		}
	//	}		
	//	
	//
	//	// now deduce underlying connectivity graph between variables
	//	// collect components : two lists with matching indexes.
	//	List<Set<Variable>> components = new ArrayList<Set<Variable>>();
	//	List<Map<ArrayPrefix,Set<Integer>>> arrComponents = new ArrayList<Map<ArrayPrefix,Set<Integer>>>();
	//	
	//	// we iterate thru all variables of GAL; these todo are emptied as the algorithm processes transitions
	//	// initially all vars and array cells need to be treated
	//	Set<Variable> todo = new HashSet<Variable>(gal.getVariables());
	//	Map<ArrayPrefix, Set<Integer>> todoArrs = new HashMap<ArrayPrefix, Set<Integer>>(constantArrs);
	//	
	//	while (! todo.isEmpty() || ! todoArrs.isEmpty()) {
	//		// this will be the new component 
	//		Set<Variable> component = new HashSet<Variable>();
	//		Map<ArrayPrefix, Set<Integer>> arrcomponent = new HashMap<ArrayPrefix,Set<Integer>>();
	//		
	//		// pop any variable from todo, and add it to the component
	//		if (!todo.isEmpty()) {
	//			Variable seed = pop(todo);
	//			component.add(seed);
	//		} else {
	//			Entry<ArrayPrefix, Set<Integer>> arrtarget = todoArrs.entrySet().iterator().next();
	//			ArrayPrefix target = arrtarget.getKey();
	//			Set<Integer> tvalues = arrtarget.getValue();
	//			Integer tindex = pop(tvalues);
	//			if (tvalues.isEmpty()) {
	//				todoArrs.remove(target);
	//			}
	//			
	//			Set<Integer> valueSet = new HashSet<Integer>();
	//			valueSet.add(tindex);
	//			arrcomponent.put(target, valueSet);
	//		}
	//					
	//		// iterate thru transitions : add all related variables of seed (transitively) to component.
	//		for (Transition t : gal.getTransitions()) {
	//			boolean domerge = false;
	//			Set<Variable> vars = varEdges.get(t);
	//			if (vars != null) {
	//				Set<Variable> varscopy = new HashSet<Variable>(vars);
	//				varscopy.retainAll(component);
	//				if (! varscopy.isEmpty()) {
	//					domerge = true;
	//				}
	//			} else {
	//				vars = Collections.emptySet();
	//			}
	//			
	//			Map<ArrayPrefix, Set<Integer>> arrs = arrEdges.get(t);
	//			if (arrs != null) {
	//				for (Entry<ArrayPrefix, Set<Integer>> entry : arrs.entrySet()) {
	//					Set<Integer> incomp = arrcomponent.get(entry.getKey());
	//					if (incomp != null) {
	//						Set<Integer> targetcopy = new HashSet<Integer>(entry.getValue()) ;
	//						targetcopy.retainAll(incomp);
	//						if (! targetcopy.isEmpty()) {
	//							domerge = true;
	//							break;
	//						}
	//					}
	//				}
	//			}
	//			
	//			if (domerge){
	//				Map<ArrayPrefix, Set<Integer>> toadd = arrEdges.get(t);
	//				if (toadd != null) {
	//					for (Entry<ArrayPrefix, Set<Integer>> entry : toadd.entrySet()) {
	//						ArrayPrefix target = entry.getKey();
	//						Set<Integer> incomp = arrcomponent.get(target);
	//						if (incomp == null) {
	//							incomp = new HashSet<Integer>(entry.getValue());
	//							arrcomponent.put(entry.getKey(), incomp);
	//						} else {
	//							incomp.addAll(entry.getValue());
	//						}
	//						
	//						Set<Integer> set = todoArrs.get(target);
	//						if (set != null) {
	//							set.removeAll(incomp);
	//							if (set.isEmpty()) 
	//								todoArrs.remove(target);
	//						}
	//					}
	//				}
	//				
	//				component.addAll(vars);						
	//				todo.removeAll(vars);
	//			}
	//		}
	//		components.add(component);
	//		arrComponents.add(arrcomponent);
	//		
	//	}
	//	
	//	Specification spec = null;
	//	
	//	if (components.size() > 1 ) {
	//		System.err.println("Found separable sub components !");
	//		spec = GalFactory.eINSTANCE.createSpecification();
	//		
	//		
	//		for (int i = 0; i < components.size(); i++) {
	//			System.err.println("\nComponent "+i);
	//			GALTypeDeclaration subgal = GalFactory.eINSTANCE.createGALTypeDeclaration();
	//			subgal.setName("Sub"+i);
	//			spec.getTypes().add(subgal);
	//			Map<Variable,Variable> mapvars = new HashMap<Variable, Variable>();
	//			for (Variable var :components.get(i)) {
	//				Variable varimg = EcoreUtil.copy(var);
	//				subgal.getVariables().add(varimg );
	//				mapvars.put(var, varimg);
	//				System.err.println(var.getName());
	//			}
	//			for (Entry<ArrayPrefix, Set<Integer>> entry : arrComponents.get(i).entrySet()) {
	//				System.err.println(entry.getKey().getName() + entry.getValue());					
	//			}
	//		}
	//	}
	//	
	//	

}


