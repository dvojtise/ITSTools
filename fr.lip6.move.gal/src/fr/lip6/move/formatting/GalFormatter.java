/*
 * generated by Xtext
 */
package fr.lip6.move.formatting;

import java.util.List;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.IGrammarAccess;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.TerminalRule;
import org.eclipse.xtext.formatting.impl.AbstractDeclarativeFormatter;
import org.eclipse.xtext.formatting.impl.FormattingConfig;
import org.eclipse.xtext.util.Pair;

/**
 * This class contains custom formatting description.
 * 
 * see : http://www.eclipse.org/Xtext/documentation/latest/xtext.html#formatting
 * on how and when to use it 
 * 
 * Also see {@link org.eclipse.xtext.xtext.XtextFormattingTokenSerializer} as an example
 */
public class GalFormatter extends AbstractDeclarativeFormatter {
	
	@Override
	protected void configureFormatting(FormattingConfig c) 
	{

		IGrammarAccess ga = getGrammarAccess() ; 
		
		// find common keywords an specify formatting for them
		for (Pair<Keyword, Keyword> pair : ga.findKeywordPairs("(", ")")) 
		{
			c.setNoSpace().after(pair.getFirst());
			c.setNoSpace().before(pair.getSecond());
		}
		
		for (Keyword comma : ga.findKeywords(",")) 
		{
			c.setNoSpace().before(comma);
		}
		
		// brackets content treatment
		for(Keyword kw : ga.findKeywords(";"))
		{
			c.setLinewrap(1).after(kw) ;
		}
		
		
		List<Pair<Keyword, Keyword>> bracketsList = ga.findKeywordPairs("{", "}") ; 
		
		c.setLinewrap(1).after(GrammarUtil.findRuleForName(ga.getGrammar(), "ML_COMMENT"));
		c.setLinewrap(1).before(GrammarUtil.findRuleForName(ga.getGrammar(), "ML_COMMENT"));

		
		for (TerminalRule tr : GrammarUtil.allTerminalRules(ga.getGrammar())) {
			if (isCommentRule(tr)) {
				c.setIndentation(tr.getAlternatives(), tr.getAlternatives());

			}
		}
		for(Keyword kw : ga.findKeywords("transition", "int", "list", "array"))
		{
			c.setLinewrap(1).before(kw) ;
		}

		// brackets treatment
		for(Pair<Keyword, Keyword> pair : bracketsList)
		{
			// a space before the first '{'
			c.setSpace(" ").before(pair.getFirst());
			// Indentation between
			c.setIndentation(pair.getFirst(), pair.getSecond());
			
			// newline after '{'
			c.setLinewrap(1).after(pair.getFirst()) ;
			
			// newline before and after '}'
			c.setLinewrap(1).before(pair.getSecond()) ;
			c.setLinewrap(2).after(pair.getSecond())  ;
			
		}
		
		 
		c.setLinewrap(0, 1, 2).before(GrammarUtil.findRuleForName(ga.getGrammar(), "SL_COMMENT")) ; 
		c.setLinewrap(0, 1, 1).after (GrammarUtil.findRuleForName(ga.getGrammar(), "ML_COMMENT")) ;
	}

	
	private boolean isCommentRule(TerminalRule term) {
		
		return "ML_COMMENT".equals(term.getName()) 
			|| "SL_COMMENT".equals(term.getName());
	}
}

