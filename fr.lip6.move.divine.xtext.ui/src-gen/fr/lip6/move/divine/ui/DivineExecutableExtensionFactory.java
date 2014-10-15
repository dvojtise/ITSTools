/*
 * generated by Xtext
 */
package fr.lip6.move.divine.ui;

import org.eclipse.xtext.ui.guice.AbstractGuiceAwareExecutableExtensionFactory;
import org.osgi.framework.Bundle;

import com.google.inject.Injector;

import fr.lip6.move.divine.ui.internal.DivineActivator;

/**
 * This class was generated. Customizations should only happen in a newly
 * introduced subclass. 
 */
public class DivineExecutableExtensionFactory extends AbstractGuiceAwareExecutableExtensionFactory {

	@Override
	protected Bundle getBundle() {
		return DivineActivator.getInstance().getBundle();
	}
	
	@Override
	protected Injector getInjector() {
		return DivineActivator.getInstance().getInjector(DivineActivator.FR_LIP6_MOVE_DIVINE_DIVINE);
	}
	
}
